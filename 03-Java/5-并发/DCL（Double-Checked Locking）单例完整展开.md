---
tags:
  - java
created: 2026-07-31
updated: 2026-07-31
---

### 一、设计动机

单例模式要求一个类全局只有一个实例。最简单的线程安全实现是给整个静态工厂方法加synchronized：

```java
public class Singleton {
    private static Singleton instance;
    private Singleton() {}
    
    public static synchronized Singleton getInstance() {
        if (instance == null) {
            instance = new Singleton();
        }
        return instance;
    }
}
```

这个方案是线程安全的，问题在于性能。instance一旦创建完成，理论上后续所有线程只需要读取它就行了，根本不需要加锁。但synchronized方法每次调用都要竞争同一把锁，哪怕只是简单读一个已经初始化好的引用，也要经过获取锁、释放锁的开销。在高并发、高频调用getInstance()的场景下（比如这个单例是个工具类，被到处调用），这把全局锁会成为明显的吞吐瓶颈。

### 二、DCL的设计思路：双重检查 + 细粒度锁

DCL的核心想法是把"加锁"这件事限制在真正需要的窗口内，也就是instance还未被创建的那一小段时间，一旦创建完成，后续调用完全不进锁。

```java
public class Singleton {
    private static volatile Singleton instance;
    private Singleton() {}
    
    public static Singleton getInstance() {
        if (instance == null) {                    // 第一次检查
            synchronized (Singleton.class) {
                if (instance == null) {             // 第二次检查
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}
```

两次null检查各自的作用不一样：

第一次检查在synchronized外面，目的是性能优化。如果instance已经不为null（绝大多数调用都是这种情况），直接跳过整个同步块，连锁都不用尝试获取，避免无意义的锁竞争。

第二次检查在synchronized里面，目的是正确性。设想线程A和线程B同时发现instance为null，都准备创建实例，A先抢到锁进入同步块，创建好instance后释放锁；这时B获得锁进入同步块，如果没有第二次检查，B会无条件地再创建一个新的Singleton，破坏了单例的唯一性。加上第二次检查后，B进来发现instance已经不是null了，就会直接跳过创建逻辑，返回A创建的那个实例。

这套设计的精妙之处在于，锁竞争只会发生在系统启动初期、instance还未创建出来的那个窗口期，一旦完成初始化，所有后续请求路径上完全没有同步开销，几乎等同于一次简单的字段读取。

### 三、不加volatile为什么会出问题（已在上一轮详细讲过，这里简要串一下逻辑）

`instance = new Singleton()`不是原子操作，会被拆成分配内存、初始化对象、引用赋值三步，JIT和CPU可能对后两步重排序。一旦重排序发生，外层第一次检查可能看到一个"指向未初始化完毕对象"的引用，直接返回出去，调用方拿到的是个半成品对象，访问字段或方法时就会出现诡异的NPE或脏数据。volatile通过内存屏障禁止这种重排序，同时保证了可见性（一个线程对instance的写，能立即被其他线程看到），这是DCL正确性的关键前提，少了它DCL就是个"看起来安全实际上有坑"的反例代码，所以也是面试中最容易被深挖的细节。

### 四、DCL相比其他写法的定位

和静态内部类、枚举单例比，DCL的优势是支持懒加载且允许传参构造（比如构造函数需要读配置文件路径之类的参数，静态内部类和枚举不太方便做到这点），劣势是写起来啰嗦、容易漏加volatile、而且依然挡不住反射攻击（通过反射调用私有构造函数可以强行再创建一个实例）和反序列化攻击（反序列化默认会调用无参构造创建新对象）。如果业务上没有懒加载的硬性需求，并且想要最简洁、最安全的写法，静态内部类或者枚举通常是更推荐的选择；DCL更适合在面试中考察候选人对JMM、指令重排序、volatile语义的理解深度，实际工程里现在用得相对没那么多了。