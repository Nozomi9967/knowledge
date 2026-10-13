---
tags:
  - java
created: 2026-07-02
updated: 2026-07-02
---
## 一、AQS如何实现ReentrantLock的公平锁与非公平锁

### AQS的核心机制回顾

AQS（AbstractQueuedSynchronizer）内部维护一个`volatile int state`代表同步状态，以及一个FIFO的CLH变体等待队列。ReentrantLock把"加锁"语义映射为对state的CAS操作：state为0表示无锁，每次加锁成功state加1（实现可重入就是同一个线程再次获取时state继续累加），解锁时state减1直到回到0。

公平锁和非公平锁的差异，核心就体现在`tryAcquire()`这个方法的实现上，差别只在"获取锁之前是否检查队列中是否有其他线程在排队"。

### 非公平锁实现

```java
final boolean nonfairTryAcquire(int acquires) {
    final Thread current = Thread.currentThread();
    int c = getState();
    if (c == 0) {
        // 不管队列里有没有人在等，直接上来就抢
        if (compareAndSetState(0, acquires)) {
            setExclusiveOwnerThread(current);
            return true;
        }
    }
    else if (current == getExclusiveOwnerThread()) {
        // 可重入逻辑
        int nextc = c + acquires;
        setState(nextc);
        return true;
    }
    return false;
}
```

非公平锁的"不公平"体现在：当state为0时，新来的线程会直接尝试CAS抢锁，完全不管AQS队列里是否已经有线程排队等待更久。这意味着一个刚调用lock()的线程，可能会插队抢在已经等待很久的线程前面拿到锁。

### 公平锁实现

```java
protected final boolean tryAcquire(int acquires) {
    final Thread current = Thread.currentThread();
    int c = getState();
    if (c == 0) {
        // 多了这一步：先检查队列中是否有前驱节点在排队
        if (!hasQueuedPredecessors() &&
            compareAndSetState(0, acquires)) {
            setExclusiveOwnerThread(current);
            return true;
        }
    }
    else if (current == getExclusiveOwnerThread()) {
        int nextc = c + acquires;
        setState(nextc);
        return true;
    }
    return false;
}
```

唯一的区别就是多了`hasQueuedPredecessors()`这个判断，它检查AQS的等待队列中，是否已经有比当前线程更早排队的节点。如果有，当前线程就老老实实去排队，不去抢这个锁，从而保证了"先来后到"的公平性。

### 为什么非公平锁默认更常用

非公平锁虽然牺牲了FIFO顺序，但换来了更高的吞吐量。原因是公平锁要求每次释放锁之后唤醒队列头部线程，被唤醒的线程从阻塞态恢复到运行态有上下文切换开销，而这段空窗期里，如果有新线程恰好来抢锁，非公平锁允许它直接拿到（这种"抢跑"反而避免了一次上下文切换），实际测试中非公平锁的吞吐量通常明显优于公平锁，这也是ReentrantLock无参构造函数默认选择非公平锁的原因。

## 二、设计一个"读写都可重入、写优先"的锁

这个问题本质上是在ReentrantReadWriteLock的基础上做改造。先回顾一下标准ReentrantReadWriteLock的设计，再说明"写优先"需要改动的地方。

### 基础设计：state的高低位拆分

复用AQS单个int类型的state字段来同时表示读锁和写锁的状态，是这类锁的经典技巧：用高16位表示读锁的持有次数，低16位表示写锁的重入次数。

```java
static final int SHARED_SHIFT = 16;
static final int SHARED_UNIT = (1 << SHARED_SHIFT);
static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

static int sharedCount(int c) { return c >>> SHARED_SHIFT; }      // 读锁次数
static int exclusiveCount(int c) { return c & EXCLUSIVE_MASK; }   // 写锁重入次数
```

### 写锁的获取与重入（tryAcquire，独占模式）

```java
protected final boolean tryAcquire(int acquires) {
    Thread current = Thread.currentThread();
    int c = getState();
    int w = exclusiveCount(c);
    if (c != 0) {
        // state不为0，说明有读锁或写锁被占用
        if (w == 0 || current != getExclusiveOwnerThread())
            return false;  // 有读锁占用，或写锁被别的线程占用，失败
        // 当前线程已经持有写锁，重入
        setState(c + acquires);
        return true;
    }
    // state为0，尝试CAS获取写锁
    if (compareAndSetState(c, c + acquires)) {
        setExclusiveOwnerThread(current);
        return true;
    }
    return false;
}
```

写锁重入逻辑很直观：当前线程已经是写锁持有者时，直接在低16位上累加。

### 读锁的获取与重入（tryAcquireShared，共享模式）

读锁重入比较麻烦，因为多个线程可能同时持有读锁，需要给每个线程单独记录它自己的重入次数，AQS官方实现用了`ThreadLocalHoldCounter`这种结构：

```java
protected final int tryAcquireShared(int unused) {
    Thread current = Thread.currentThread();
    int c = getState();
    // 如果有写锁，且不是当前线程持有，读锁获取失败（保证写锁的独占性）
    if (exclusiveCount(c) != 0 && getExclusiveOwnerThread() != current)
        return -1;
    int r = sharedCount(c);
    if (compareAndSetState(c, c + SHARED_UNIT)) {
        // 记录当前线程的读锁重入次数（用ThreadLocal）
        firstReader/readHolds 等结构维护每个线程的计数
        return 1;
    }
    return fullTryAcquireShared(current); // CAS失败时的重试逻辑
}
```

### 关键改造点："写优先"

标准ReentrantReadWriteLock默认是非公平模式，读锁和写锁在抢锁时基本是谁先CAS成功谁赢，并不会特意让写锁优先。要做成"写优先"，需要在读锁获取的逻辑里加一个额外的判断：只要队列里有写线程在等待，新来的读线程就必须排队，不能插队获取读锁，避免持续不断的读请求把写线程"饿死"。

这一逻辑可以通过重写`readerShouldBlock()`来实现：

```java
// 自定义的"写优先"策略
final boolean readerShouldBlock() {
    // 只要等待队列中存在线程在等待获取锁（不管读写），
    // 且队列头部等待的是写线程，新读请求就应该阻塞
    return apparentlyFirstQueuedIsExclusive();
}
```

`apparentlyFirstQueuedIsExclusive()`检查AQS队列的第一个等待节点是否是独占模式（即写锁请求）。如果是，说明有写线程正在排队等待，这时候新来的读线程必须让路，调用`tryAcquireShared`时直接走失败分支进入队列等待，而不是抢先拿到读锁。

而写锁本身因为已经是独占模式，不需要额外改动，它的`tryAcquire`本身就要求state为0（没有任何读写锁占用）才能成功，所以只要持续不断地有读线程在排队等待的时候不让新读线程插队，写线程自然能够更快地等到"无读锁占用"这一时刻并优先获取锁。

完整效果就是：写线程到来后，后续新来的读线程都会被挡在队列里，直到这个写线程完成它的临界区操作并释放锁，从而避免经典的"写饥饿"问题，这也是数据库锁、缓存系统中"写优先读写锁"的常见实现思路。