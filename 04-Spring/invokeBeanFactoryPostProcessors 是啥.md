### **它不是某个处理器，而是 refresh() 里的一个步骤——名字直译就是「把容器里所有的 BeanFactoryPostProcessor 挨个回调一遍」。BeanFactoryPostProcessor（BFPP）是 Spring 的一个扩展点接口：bean 定义加载完、但一个 bean 都还没实例化之前，允许你修改 BeanFactory。CCPP 就是在这一步被叫起来干活的**

#### **先看它站在哪：refresh() 的第三排**

```java
public void refresh() {
    prepareBeanFactory(beanFactory);
    postProcessBeanFactory(beanFactory);              // 空模版方法，给子类覆写的钩子
    invokeBeanFactoryPostProcessors(beanFactory);     // ← 你问的这一步
    registerBeanPostProcessors(beanFactory);
    // ... onRefresh、finishBeanFactoryInitialization 等
}
```

顺手分清三个长得像的名字：`postProcessBeanFactory()`（没有 rs）是 context 上的空钩子方法；`BeanFactoryPostProcessor` 是扩展点接口；`invokeBeanFactoryPostProcessors()`（复数）是回调动作本身——「invoke …s」，把一堆处理器挨个执行。

#### **BeanFactoryPostProcessor 是个啥**

接口就一个方法：

```java
@FunctionalInterface
public interface BeanFactoryPostProcessor {
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory);
}
```

时机的本质：此刻工厂里只有「图纸」（BeanDefinition），还没有「成品」（bean 实例），所以 BFPP 改的是**图纸层面**——增删改 bean 定义、改属性值、改工厂行为。一个最贴切的比喻：**bean 定义是菜单，bean 实例是端上来的菜；BFPP 是在开炒之前改菜单的，BeanPostProcessor 是在菜出锅前后加料的。** 你早就用过的两个东西其实是 BFPP：解析 `${...}` 占位符的 PropertySourcesPlaceholderConfigurer、MyBatis 扫 Mapper 接口的 MapperScannerConfigurer，都是在这个窗口期工作的。

#### **这一步内部怎么跑：先子接口、后普通接口**

它委托给一个工具类执行，规则两条。第一，**BDRPP（BeanDefinitionRegistryPostProcessor）优先**——它是 BFPP 的子接口，多一个 `postProcessBeanDefinitionRegistry()` 方法，专门用来**注册新的 BeanDefinition**。能「加图纸」的必须先跑，跑完新加进来的定义（包括随之注册的其他处理器）才能被后面的环节看到，所以还有个小循环反复捞新出现的 BDRPP 直到捞干净。第二，普通 BFPP 随后执行，两类内部都按 PriorityOrdered → Ordered → 无排序 的顺序回调。

CCPP 之所以是「真正干活的」，正因为它同时实现了这两个方法、在这里被连着调了两次：

| CCPP 的方法 | 属于哪个接口 | 干什么 |
| --- | --- | --- |
| postProcessBeanDefinitionRegistry | BDRPP | 解析主启动类：@ComponentScan 扫业务类、@Import 导入自动配置类并条件过滤，全部注册成 BeanDefinition |
| postProcessBeanFactory | BFPP | 对 full 模式 @Configuration 做 CGLIB 增强 |

而 CCPP 本身哪来的？之前埋过一句「创建 context 时内置注册的」——`new` 容器时 AnnotatedBeanDefinitionReader 构造器里通过 AnnotationConfigUtils 把它作为 BeanDefinition 提前塞进了工厂。所以走到 invoke 这一步时，它已经在工厂里等着被回调了；**「注册」发生在建容器时，「干活」发生在这**，这就是当初把你原答案里「refresh 会回调所有 BFPP」和「CCPP 加载配置类」分开说的原因。

#### **和下一步的对比：这是高频考点的标准形态**

| | BeanFactoryPostProcessor | BeanPostProcessor |
| --- | --- | --- |
| 操作对象 | BeanDefinition（图纸） | Bean 实例（成品） |
| 时机 | 实例化之前，整个启动只跑一轮 | 每个 bean 创建时都走，实例化前后各一钩子 |
| 典型代表 | CCPP、占位符解析器 | Autowired 注解处理器、AOP 代理创建器 |

「BeanFactoryPostProcessor 和 BeanPostProcessor 的区别」本身就是超高频题，答案是上表；再补一句串联全程的收尾就完整了：**invokeBeanFactoryPostProcessors 只把图纸注册齐，图纸变成真正的 bean 是后面 finishBeanFactoryInitialization 的事**——自动配置类此刻还只是 BeanDefinition，说的就是这一点。

#### **面试话术**

被问「invokeBeanFactoryPostProcessors 是干嘛的」就这套：

> 它是 refresh 里回调所有 BeanFactoryPostProcessor 的步骤。BFPP 是 bean 实例化之前改 BeanDefinition 的扩展点，其中子接口 BeanDefinitionRegistryPostProcessor 先执行，因为它能注册新的 bean 定义。Boot 里真正干活的是内置注册的 ConfigurationClassPostProcessor，它是个 BDRPP，在这一步以主启动类为入口解析配置类、完成扫描和自动配置导入，随后又以 BFPP 身份对 @Configuration 做 CGLIB 增强。业务 bean 的 BeanDefinition 就是在这一步注册进工厂的，真正的实例化在之后的 finishBeanFactoryInitialization。

这段话能把启动流程、自动装配、BFPP/BPP 区别三道题一次缝住——每次被问其中一个，你都有机会顺手展示另外两个。