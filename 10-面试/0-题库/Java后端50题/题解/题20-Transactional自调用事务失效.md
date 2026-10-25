---
tags:
  - java
created: 2026-07-02
updated: 2026-07-02
---
### **Spring 自调用事务失效的根源在于 AOP 代理机制：同一个类内部方法直接调用时走的是原始对象的 `this` 引用，绕过了代理，导致 `REQUIRES_NEW` 的事务切面根本没被触发。**

  

## **原理讲解**

  

Spring 的 `@Transactional` 是基于 AOP 实现的，默认用 JDK 动态代理或 CGLIB 生成一个代理类。当外部调用者拿到的是 Spring 容器注入的 Bean 时，其实拿到的是**代理对象**，而不是原始类。事务的开启、提交、回滚这些逻辑，都由代理对象在方法调用前后通过拦截器（`TransactionInterceptor`）来织入。

  

关键在于：**代理只能拦截"从外部进入代理对象"的方法调用**。

  

假设有这样一段代码：

  

```java

@Service

public class OrderService {

  

@Transactional

public void methodA() {

// ...

this.methodB(); // ← 问题就在这里

}

  

@Transactional(propagation = Propagation.REQUIRES_NEW)

public void methodB() {

// ...

}

}

```

  

当外部调用 `orderService.methodA()` 时，走的是代理对象，事务切面正常生效。但在 `methodA` 内部执行 `this.methodB()` 时，这个 `this` 指向的是**原始的 target 对象**，而不是代理对象。因此这次调用**根本不经过代理**，`TransactionInterceptor` 拿不到执行机会，`methodB` 上的 `REQUIRES_NEW` 注解就形同虚设，结果就是两个方法共用了 `methodA` 的那个事务，甚至没有独立事务。

  

一句话总结：**代理增强的是"引用"，不是"方法本身"。自调用用的 `this` 引用不是代理引用，所以增强不生效。**

  

## **三种解决办法**

  

**第一种：把自调用方法抽到另一个 Bean 里。**

  

这是最推荐、最符合面向对象设计的做法。将 `methodB` 拆到另一个 Service（例如 `OrderInnerService`）中，然后在 `OrderService` 里通过依赖注入拿到它再调用。因为注入进来的是代理对象，所以调用会经过代理，事务切面正常触发。这种方式还顺带解决了职责耦合的问题。

  

**第二种：注入自己（自注入），或者从 `ApplicationContext` 拿到自己的代理。**

  

在同一个类内部，通过 `@Autowired` 把自己作为一个 Bean 注入进来，然后用注入的引用去调用：

  

```java

@Autowired

private OrderService self;

  

@Transactional

public void methodA() {

self.methodB(); // 走代理，事务生效

}

```

  

或者用 `AopContext.currentProxy()` 拿到当前代理对象，但前提是要在启动类上开启 `@EnableAspectJAutoProxy(exposeProxy = true)`，然后写成 `((OrderService) AopContext.currentProxy()).methodB()`。这种方式无需拆类，但可读性略差。

  

**第三种：切换到 AspectJ 编译期/加载期织入（LTW）。**

  

Spring 默认用的是运行时的动态代理，只能拦截外部调用。而 AspectJ 是**字节码层面**的织入，它直接把事务逻辑织入到方法字节码里，不依赖代理对象。因此无论是外部调用还是 `this` 自调用，都能正常触发事务。做法是把事务管理模式改成 `@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)`，并引入 `spring-aspects` 依赖，配合编译期或 LTW 织入。这种方式最彻底，但配置复杂，一般项目里用得较少。

  

面试口述的时候，重点讲清楚**"代理对象 vs 原始对象"**、**"this 绕过代理"**这两个核心概念，然后给出三种方案并说明各自的适用场景，基本就能拿满分。