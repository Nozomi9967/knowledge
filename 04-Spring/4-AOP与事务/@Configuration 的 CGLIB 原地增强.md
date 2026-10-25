---
tags:
  - spring
  - springboot
  - cglib
  - 代理
  - 面试题
created: 2026-06-08
updated: 2026-06-08
source: Spring Framework 源码 ConfigurationClassPostProcessor / ConfigurationClassEnhancer
---

# @Configuration 的 CGLIB 原地增强

> 一句话定位：`@Configuration` 类默认会被 Spring 用 CGLIB 生成一个子类替换掉，让你在配置类里写 `this.otherBeanMethod()` 时拿到的仍是容器里的单例，而不是 `new` 出来的新对象。

这是 [[SpringBoot热门面试题-优化纯净版]] Q4 的展开页。Q4 只提了一句「配置类默认会被 CGLIB 代理增强，保证 Bean 方法之间互相调用时拿到的是同一个单例」，这里把「为什么、怎么做、增强体现在哪、和 AOP 自调用失效为什么不冲突」讲透。

---

## 一、要解决什么问题

配置类里一个 `@Bean` 方法调用另一个 `@Bean` 方法是极常见的写法：

```java
@Configuration
public class AppConfig {

    @Bean
    public DataSource dataSource() {
        return new HikariDataSource();          // 每次调用都 new 一个
    }

    @Bean
    public JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(dataSource());  // ← 直接方法调用
    }
}
```

**如果没有增强，这会出真实的线上故障。**

`jdbcTemplate()` 里那句 `dataSource()` 只是一次普通 Java 方法调用，直接 `new` 一个全新 `HikariDataSource`。于是容器里出现**两个数据源**：

- 一个是 `@Bean` 方法注册进容器的 `dataSource`
- 一个是「顺手 new 出来、塞进 `JdbcTemplate`」的匿名数据源

事务管理器注入的是前者，`JdbcTemplate` 用的是后者 → 连接池开两份、事务管不住 SQL、连接数翻倍。这种 bug 极难排查，因为代码看起来完全正常。

---

## 二、Spring 的解法：CGLIB 子类 + 方法拦截

`ConfigurationClassPostProcessor`（一个 `BeanFactoryPostProcessor`）解析完配置类后，调用 `ConfigurationClassEnhancer` 给配置类**生成 CGLIB 子类**，并注册两个 callback：

| Callback | 拦截对象 |
|---|---|
| `BeanMethodInterceptor` | 所有 `@Bean` 方法 |
| `BeanFactoryAwareMethodInterceptor` | `setBeanFactory`，让代理类能拿到 BeanFactory |

增强后的逻辑等价于：

```java
public class AppConfig$$EnhancerBySpringCGLIB extends AppConfig {

    @Override
    public DataSource dataSource() {
        BeanFactory bf = this.$$beanFactory;
        // 1. 先问容器：有没有叫 "dataSource" 的 Bean？
        if (bf.containsBean("dataSource")) {
            return bf.getBean("dataSource");          // ✅ 有就直接返回单例
        }
        // 2. 没有才执行你写的原始逻辑，并注册进容器
        return super.dataSource();
    }
}
```

于是 `jdbcTemplate()` 里那句 `dataSource()` 实际调用的是**被覆写的代理方法**，走 `getBean("dataSource")`，拿到的是同一个单例。

> [!warning] beanName 就是方法名
> 拦截器拿方法名当 beanName 去容器里查，所以 `@Bean` 方法名和 `@Bean(name = ...)` 要保持一致，别乱改。

---

## 三、关键问题：为什么看源码一点变化都没有

**因为改的不是源码，是运行时的字节码。**

源码没变、编译产物没变、`target/classes/AppConfig.class` 一个字节都没变。变的是运行期多出来的三样东西：

| 层级 | 发生了什么 |
|---|---|
| ① 内存里多个类 | 生成 `AppConfig$$EnhancerBySpringCGLIB$$a1b2c3d4`，**是 `AppConfig` 的子类**，不落盘 |
| ② BeanDefinition 被改写 | `beanDef.setBeanClass(enhancedClass)`，原本指向 `AppConfig`，被换成增强类 |
| ③ `getBean` 拿到的实例 | 这个 bean 的**实际类型**是增强子类，不是 `AppConfig` |

关键在 ②：`ConfigurationClassPostProcessor` 是 **BeanFactoryPostProcessor**，在 `invokeBeanFactoryPostProcessors` 阶段执行——**此时所有单例 Bean 都还没实例化**。改掉 BeanDefinition 的 `beanClass` 后，后续按 BeanDefinition 创建实例时 new 出来的自然就是增强类。

**顺序是「先换图纸，再按图纸盖房」。**

自己验证：

```java
AppConfig cfg = ctx.getBean(AppConfig.class);
System.out.println(cfg.getClass().getName());
// com.example.AppConfig$$EnhancerBySpringCGLIB$$a1b2c3d4
System.out.println(AppConfig.class == cfg.getClass());   // false
```

`getBean(AppConfig.class)` 能返回，是因为**按类型匹配允许子类**。

### 生成的字节码长什么样

`ConfigurationClassEnhancer` 调 cglib 的 `Enhancer`，配了这些：

```java
enhancer.setSuperclass(configClass);                                     // 继承你的配置类
enhancer.setInterfaces(EnhancedConfiguration.class);                     // 额外实现一个接口
enhancer.setUseFactory(false);                                           // 不生成 CGLIB$FACTORY 静态字段（防类加载器泄漏）
enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE);                   // 起名 AppConfig$$EnhancerBySpringCGLIB$$xxx
enhancer.setStrategy(new BeanFactoryAwareGeneratorStrategy(classLoader)); // 加一个 $$beanFactory 字段
enhancer.setCallbackFilter(CALLBACK_FILTER);                             // 哪些方法走哪个拦截器
Class<?> enhanced = enhancer.createClass();                              // 只生成字节码，不建实例
```

生成出来的类，用 `javap` 反编译大致是：

```java
public class AppConfig$$EnhancerBySpringCGLIB$$a1b2c3d4
        extends AppConfig implements EnhancedConfiguration, BeanFactoryAware {

    private BeanFactory $$beanFactory;                    // BeanFactoryAwareGeneratorStrategy 塞进来的

    // ① 原始方法体被「改名保全」在这里
    final DataSource CGLIB$dataSource$0() {
        return new HikariDataSource();
    }

    // ② 你原来的方法名被覆写，方法体换成了「转发给拦截器」
    public DataSource dataSource() {
        MethodInterceptor mi = this.CGLIB$CALLBACK_1;      // BeanMethodInterceptor
        return (DataSource) mi.intercept(this, AppConfig.class.getMethod("dataSource"),
                new Object[0], this.CGLIB$CALLBACK_1$MethodProxy);
    }

    // ③ setBeanFactory 也被拦，用来把 $$beanFactory 填上
    public void setBeanFactory(BeanFactory bf) { ... }
}
```

**要点：你的代码没消失，是被改名成 `CGLIB$dataSource$0` 藏起来了**，原名位置换成一个转发壳子。拦截逻辑写在 Java 类里（`ConfigurationClassEnhancer$BeanMethodInterceptor`），**不在字节码里**，字节码只负责「把调用导流过去」。

### 怎么亲眼看到

| 方式 | 做法 |
|---|---|
| dump 到磁盘 | 启动加 `-Dcglib.debugLocation=./cglib-dump`，跑完在目录下得到 `.class`，再 `javap -p -c` 反编译 |
| Arthas | `sc -d com.example.AppConfig` 看加载了哪些类，`jad com.example.AppConfig\$\$EnhancerBySpringCGLIB\$\$a1b2c3d4` 直接反编译运行中的类 |
| 最土最快 | `cfg.getClass().getName()`，看到 `$$EnhancerBySpringCGLIB$$` 就成了 |

> `-Dcglib.debugLocation` 这个属性名从 Spring 4 起保留至今，虽然 cglib 已被 repackage 进 `org.springframework.cglib`。

### 两个容易卡住的细节

**① 生成子类不需要调你的构造器。** 按常理 `new` 子类必须先跑父类构造器，那配置类构造器会执行两次？不会——Spring 用 **Objenesis**（`ReflectionFactory` + `Unsafe`）绕过构造器直接分配对象，配置类构造器只按正常 bean 创建流程跑一次。

**② 为什么磁盘上没有这个 `.class`。** cglib 用 `ReflectUtils.defineClass()` 配自定义 `ClassLoader`，直接把字节数组 `defineClass` 进方法区。整条链路是「`byte[]` → 内存里的 `Class` 对象」，从头到尾没碰文件系统。

---

## 四、和 AOP 自调用失效为什么不冲突

[[spring八股]] 和 [[Spring-interview-guide]] 里都有一条经典结论：**同一个 bean 内方法互调会绕过代理，`@Transactional` / `@Async` 失效**。那配置类这里为什么不受影响？

**因为代理方式完全不同。**

### AOP：wrap（包装），有独立 target

```
容器里的 bean = proxyA  ──(持有)──>  targetA
                                    ↑ 真正干活的对象
```

容器把 `proxyA` 给你，你调 `proxyA.methodA()`：

1. 拦截器开事务 → `proxyA` 把调用转给 `targetA.methodA()`
2. **`targetA.methodA()` 执行时，里面的 `this` 是 `targetA`，不是 `proxyA`**
3. `this.methodB()` 直接打在 `targetA` 上，压根不经过 `proxyA` → 切面失效

### @Configuration：enhance（原地增强），没有 target

```
容器里的 bean = AppConfig$$Enhancer（子类实例）   ← 只有这一个对象
```

CGLIB 子类里 `jdbcTemplate()` 的方法体，**是在增强实例上执行的**，所以里面的 `this` 就是这个增强实例本身：

```java
public class AppConfig$$EnhancerBySpringCGLIB extends AppConfig {

    @Override
    public JdbcTemplate jdbcTemplate() {
        // ↓ 这里的 this = 增强实例（就是容器里那个 bean）
        // this.dataSource() 是虚方法调用 → 分派到被覆写的版本 → 拦截生效
        return new JdbcTemplate(this.dataSource());
    }

    @Override
    public DataSource dataSource() {
        // 拦截器逻辑：按方法名去容器 getBean，有就直接返回单例
        ...
    }
}
```

**`this.dataSource()` 走的正是被覆写的 `dataSource()`**，因为 `this` 的静态/动态类型都是子类，虚方法分派必然落到覆写版本上。

### 对比表

| | `@Transactional` / `@Async`（AOP） | `@Configuration` 的 `@Bean` |
|---|---|---|
| 代理方式 | **wrap**：包住一个 target | **enhance**：原地换成子类 |
| 对象数 | 2 个（proxy + target） | **1 个**（增强实例自己就是 bean） |
| 方法体里的 `this` | target → 自调用绕过 | 增强实例 → 自调用**打到覆写版，不绕过** |

> 所以「同一个 bean 内方法互调会绕过代理」的准确表述是：**同一个 target 内互调**会绕过代理。配置类根本没有 target 这一层。

---

## 五、为什么容器自己调工厂方法不会无限递归

既然拦截器每次都 `getBean("dataSource")`，那容器为了**创建** `dataSource` 而去调 `dataSource()` 工厂方法时，岂不是又 `getBean("dataSource")` → 死循环？

拦截器第一步就先判断：

```java
if (isCurrentlyInvokedFactoryMethod(beanMethod)) {
    // 当前正在执行的工厂方法就是我自己 → 说明是容器在创建我
    return cglibMethodProxy.invokeSuper(...);   // 老老实实执行原始方法体
}
// 否则才去容器里查/取
Object resolvedBean = resolveBeanReference(...);
```

判断依据是 `ConstructorResolver` 里的一个 ThreadLocal（`currentlyInvokedFactoryMethod`）。所以：

- 你在 `jdbcTemplate()` 里调 `dataSource()`：正在执行的工厂方法是 `jdbcTemplate`，与 `dataSource` 不等 → 走 `getBean` 分支；若 `dataSource` 未创建，`getBean` 现建一个（此时 ThreadLocal 是 `dataSource`，原始方法体正常执行），建好返回；以后再调直接命中单例。
- 容器创建 `dataSource` 时调 `dataSource()`：ThreadLocal 命中自己 → 直接执行原始方法体，不再 `getBean`。

---

## 六、Full 模式与 Lite 模式

```java
@Configuration(proxyBeanMethods = false)   // Lite 模式，不生成 CGLIB 子类
public class AppConfig { ... }
```

| | Full 模式（`true`，**默认**） | Lite 模式（`false`） |
|---|---|---|
| CGLIB 代理 | 有 | 无 |
| 方法间调用 | 走容器，返回单例 | 直接 `new`，**多个实例** |
| 启动速度 | 慢一点（要生成代理类） | 快 |
| 适用 | 方法之间有依赖调用 | 方法彼此独立，或改用方法参数注入 |

**Spring Boot 自己的自动配置类全是 `proxyBeanMethods = false`**——因为自动配置类动辄上百个，生成代理会拖慢启动；而且它们约定不用方法间调用，需要依赖就直接写在方法参数上：

```java
@Bean
public JdbcTemplate jdbcTemplate(DataSource dataSource) {   // 参数注入，不靠 this 调用
    return new JdbcTemplate(dataSource);
}
```

### 哪些类算 Full、哪些算 Lite

| 类型 | 模式 |
|---|---|
| `@Configuration`（`proxyBeanMethods` 默认 true） | **Full** |
| `@Configuration(proxyBeanMethods = false)` | Lite |
| `@Component` / `@ComponentScan` 扫到的类 | Lite |
| `@Import` 进来的普通类 | Lite |

所以「在 `@Component` 类里写两个 `@Bean` 方法互相调用」是会出多个实例的，**这是个真实的坑**。

---

## 七、什么情况下会真的绕过（失效场景清单）

| 场景 | 为什么绕过 |
|---|---|
| `@Bean` 方法是 **`final`** | CGLIB 靠继承，final 方法无法覆写 → 不拦截，直接 `new` |
| `@Bean` 方法是 **`private`** | 同上，private 不可覆写 |
| `@Bean` 方法是 **`static`** | static 不参与虚方法分派，无法覆写 → 永远 lite 行为 |
| `proxyBeanMethods = false` | 压根不生成 CGLIB 子类 |
| 写在 `@Component` / `@Import` 类里 | 不是 `@Configuration`，不做增强（Lite） |
| 自己 `new AppConfig().dataSource()` | 你 new 的是原始类，不是增强实例 |

> 顺带一个实践点：`BeanFactoryPostProcessor` / `BeanPostProcessor` 的 `@Bean` 方法建议写成 **`static`**——避免配置类过早被实例化，导致它自己的后置处理器还没注册好。这里「static 不被增强」反倒成了优点。

---

## 八、面试话术（口述版）

> 「`@Configuration` 默认是 Full 模式，Spring 用 CGLIB 给配置类生成一个子类，把每个 `@Bean` 方法覆写掉。覆写后的逻辑是先按方法名去容器里 `getBean`，有就直接返回单例，没有才执行原始方法体并注册。这样配置类内部方法互相调用时，拿到的也是容器里那个单例，而不是 `new` 出来的新对象。不这么做的话，`jdbcTemplate` 里调 `dataSource` 就会 `new` 出第二个数据源，事务管理器管一个、`JdbcTemplate` 用另一个，线上直接出问题。
>
> 至于增强体现在哪，改的不是源码是运行时字节码。`ConfigurationClassPostProcessor` 是个 BeanFactoryPostProcessor，在所有单例实例化之前，把配置类 BeanDefinition 的 `beanClass` 换成增强类——先换图纸再盖房。生成的子类里，你原来的方法体被改名成 `CGLIB$xxx$0` 保留下来，原名位置换成转发到 `BeanMethodInterceptor` 的空壳。这个类只在内存里 `defineClass`，不落盘，加 `cglib.debugLocation` 能 dump 出来，或者用 Arthas 的 `jad` 反编译。
>
> 有人会问这和 `@Transactional` 自调用失效是不是矛盾，不矛盾，因为代理方式不同。AOP 的代理是包在目标对象外面，方法体里 `this` 指向目标对象本身，所以自调用不经过代理；配置类的 CGLIB 是原地增强，容器里那个 bean 就是增强子类实例，没有独立的目标对象，方法体里 `this` 就是增强实例，`this.dataSource()` 是虚方法调用、必然落到覆写版本上。
>
> 真正会失效的是 `@Bean` 方法写成 `final`、`private`、`static`，或者关掉 `proxyBeanMethods`，或者把 `@Bean` 写在 `@Component` 类里。另外 Full 模式是有成本的——要生成代理类、每次调用多一层拦截，所以 Boot 自己的自动配置类统一用 `proxyBeanMethods = false`，依赖靠方法参数注入。」

---

## 通俗理解

配置类就像一个「小工厂」，你写了张配方（`@Bean` 方法）说「需要螺丝就从我自己这拿」。但 Spring 怕你每次「从我自己的仓库拿」时都开一条新产线造一个，于是**在你背后把你的厂子改造成了一个带自动领料系统的厂子**：你说「拿螺丝」，它先去总仓库查有没有现成的，有就直接给你那一颗，没有才照你的配方现造一颗并入库。

你手里那份配方纸（源码）当然没变，变的是**运行起来的那座厂子本身**——它被换成了一个功能更多的仿制版（CGLIB 子类）。而 AOP 那种代理不一样，它是在你的厂子**外面加了一道门卫**：门卫负责登记进出（事务、日志），但你厂子里两个车间之间的内部走动（`this` 调用）根本不经过门卫，所以门卫管不着。

---

## 关联知识点

- [[SpringBoot热门面试题-优化纯净版]] — Q4 的展开页，Q4 只给了结论句
- [[spring八股]] — Spring 全家桶八股总览，AOP 代理与 Bean 生命周期
- [[Spring-interview-guide]] — Spring 七大模块面试题口述版，AOP 部分有自调用失效的经典结论
- [[@SpringBootApplication解析]] — 组合注解里 `@SpringBootConfiguration` 即 `@Configuration` 的派生
- [[Spring ioc]] — IoC 容器原理，BeanDefinition 与 getBean 流程
- [[SpringBoot热门面试题-优化纯净版]] — 原卷（无答案版，自测用）