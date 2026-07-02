

### 一、为什么需要读写锁

ReentrantLock是互斥锁，同一时刻只允许一个线程持有，不管这个线程是要读数据还是写数据。但实际业务场景里，读操作往往远多于写操作，而且多个线程同时读数据并不会造成数据不一致问题（只要没人在写）。如果用普通互斥锁，读线程之间也要互相排队，白白浪费了并发能力。

ReentrantReadWriteLock的核心思想是把锁拆成两把：读锁（共享锁）和写锁（独占锁）。读锁允许多个线程同时持有，提高并发吞吐；写锁依然是独占的，保证数据一致性。它们之间的互斥规则是：读读不互斥，读写互斥，写写互斥。

### 二、内部结构：一个state表示两把锁

最巧妙的设计是它没有用两个独立的状态变量去分别表示读锁和写锁，而是复用AQS中唯一的`int state`字段，通过按位拆分实现"一个state管两把锁"。

```java
static final int SHARED_SHIFT   = 16;
static final int SHARED_UNIT    = (1 << SHARED_SHIFT);  // 0x00010000
static final int MAX_COUNT      = (1 << SHARED_SHIFT) - 1;
static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1; // 0x0000FFFF

static int sharedCount(int c)    { return c >>> SHARED_SHIFT; }     // 高16位：读锁持有数
static int exclusiveCount(int c) { return c & EXCLUSIVE_MASK; }     // 低16位：写锁重入数
```

一个32位的int被切成两半：高16位记录当前持有读锁的总次数（多个线程的读锁重入次数之和），低16位记录写锁的重入次数（只有一个线程能持有写锁，所以低16位就是这个线程的重入计数）。这也解释了为什么读写锁的最大重入/并发数是65535（2的16次方减1）——这是这个设计取舍的代价。

### 三、写锁的获取逻辑（独占模式 tryAcquire）

```java
protected final boolean tryAcquire(int acquires) {
    Thread current = Thread.currentThread();
    int c = getState();
    int w = exclusiveCount(c);
    if (c != 0) {
        // c != 0 说明要么有读锁占用，要么有写锁占用
        if (w == 0 || current != getExclusiveOwnerThread())
            // w == 0 说明是读锁占用（写锁次数为0但state不为0）→ 写锁获取失败
            // 或者写锁被别的线程占用 → 失败
            return false;
        if (w + exclusiveCount(acquires) > MAX_COUNT)
            throw new Error("Maximum lock count exceeded");
        // 当前线程已持有写锁，重入
        setState(c + acquires);
        return true;
    }
    // c == 0，没有任何锁占用，可以尝试获取写锁
    if (writerShouldBlock() ||
        !compareAndSetState(c, c + acquires))
        return false;
    setExclusiveOwnerThread(current);
    return true;
}
```

关键判断在于`w == 0 || current != getExclusiveOwnerThread()`这一行：如果state不为0但写锁计数w为0，说明此时是有读锁在被占用，写锁必须等待所有读锁释放后才能获取，这正是"读写互斥"的体现。

### 四、读锁的获取逻辑（共享模式 tryAcquireShared）

```java
protected final int tryAcquireShared(int unused) {
    Thread current = Thread.currentThread();
    int c = getState();
    if (exclusiveCount(c) != 0 &&
        getExclusiveOwnerThread() != current)
        // 有写锁占用，且不是当前线程自己（避免和自己的写锁死锁）
        return -1;
    int r = sharedCount(c);
    if (!readerShouldBlock() &&
        r < MAX_COUNT &&
        compareAndSetState(c, c + SHARED_UNIT)) {
        // CAS成功，读锁数+1（注意是加SHARED_UNIT，即加到高16位上）
        if (r == 0) {
            firstReader = current;
            firstReaderHoldCount = 1;
        } else if (firstReader == current) {
            firstReaderHoldCount++;
        } else {
            HoldCounter rh = cachedHoldCounter;
            if (rh == null || rh.tid != getThreadId(current))
                cachedHoldCounter = rh = readHolds.get();
            else if (rh.count == 0)
                readHolds.set(rh);
            rh.count++;
        }
        return 1;
    }
    return fullTryAcquireShared(current);  // CAS失败的重试与边界处理
}
```

第一个判断很直观：如果有写锁被别的线程占用，读锁直接获取失败，体现读写互斥。

比较特殊的是读锁重入次数的记录方式。因为同一时刻可能有多个线程都持有读锁，不能像写锁那样简单用一个int字段记录重入次数（那样无法区分是哪个线程重入了几次），所以这里引入了一套精巧的优化：

`firstReader`和`firstReaderHoldCount`专门缓存第一个获取读锁的线程及其重入次数，因为很多场景下第一个读线程往往会持续操作，直接缓存能避免一次ThreadLocal查找。

`cachedHoldCounter`缓存最近一个操作读锁的线程的计数器，同样是为了避免每次都查ThreadLocal带来的开销。

如果都没命中缓存，才会真正走`ThreadLocal<HoldCounter> readHolds`，这是一个以线程为key记录各自重入次数的ThreadLocal，保证每个线程的读锁重入计数互不干扰。

这套"双重缓存 + ThreadLocal兜底"的设计是为了优化读锁高频获取场景下的性能，避免每次都付出ThreadLocal查找的开销。

### 五、公平与非公平模式下读写锁的抢占策略

`writerShouldBlock()`和`readerShouldBlock()`这两个方法在公平和非公平实现里逻辑不同。

非公平模式下，`writerShouldBlock()`恒返回false（写锁可以随时插队尝试），而`readerShouldBlock()`的实现稍微复杂，它会调用`apparentlyFirstQueuedIsExclusive()`检查队列头部是否是写线程在等待，如果是，新来的读线程会被要求排队，这其实是非公平读写锁里自带的一种"防止写饥饿"的保护机制（避免读请求源源不断导致写线程永远抢不到锁）。

公平模式下，无论读锁还是写锁，`xxxShouldBlock()`都会调用`hasQueuedPredecessors()`检查是否有更早的线程在排队，严格遵守FIFO顺序。

### 六、读写锁的局限性：锁降级与升级

ReentrantReadWriteLock支持锁降级（持有写锁的线程，可以在不释放写锁的情况下再去获取读锁，然后释放写锁，从而平滑过渡到只持有读锁），这是为了保证数据的强一致性，典型用法是：

```java
void processData() {
    writeLock.lock();
    try {
        if (!dataUpToDate) {
            updateData();
        }
        readLock.lock();  // 锁降级：先拿读锁
    } finally {
        writeLock.unlock();  // 释放写锁，此时仍持有读锁
    }
    try {
        useData();
    } finally {
        readLock.unlock();
    }
}
```

但它**不支持锁升级**，也就是持有读锁的线程不能直接升级为写锁。原因很简单：如果允许多个持有读锁的线程同时尝试升级为写锁，会造成死锁（每个线程都在等待其他读锁释放，但大家都不释放，互相卡死）。这是面试中经常被追问的一个点，回答时要明确"为什么不支持"，而不是只说"不支持"。

### 七、典型应用场景

ReentrantReadWriteLock适合读多写少、且读操作耗时相对较长（不只是简单的字段读取，可能涉及一些计算）的场景，比如本地缓存的实现（缓存读取频繁，更新频率低）、配置中心的本地副本（配置很少变更但被频繁读取查询）。如果读操作本身非常轻量（比如只是读一个原子变量），用ReentrantReadWriteLock的开销可能反而比简单的volatile或者无锁结构更大，这时候可以考虑用`StampedLock`（Java 8引入，性能比ReadWriteLock更好，但API更复杂、且不支持重入，是它的进一步优化版本）替代。