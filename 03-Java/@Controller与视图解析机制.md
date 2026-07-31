---
tags:
  - SpringMVC
  - Controller
  - 视图解析
  - JSP
  - RESTful
created: 2026-07-02
updated: 2026-07-12
source: 03-Java/SpringMVC.md
---

# @Controller vs @RestController 与视图解析机制

## 一、核心区别

| 维度         | `@Controller`            | `@RestController`               |
| ---------- | ------------------------ | ------------------------------- |
| 本质         | `@Controller`            | `@Controller` + `@ResponseBody` |
| 返回值默认行为    | 当作**视图名**（逻辑视图名），走视图解析流程 | 当作**响应体数据**（如 JSON），直接写给客户端     |
| 返回 HTML 页面 | ✅ 可以                     | ❌ 不适用                           |
| 返回 JSON 数据 | ❌ 需手动加 `@ResponseBody`   | ✅ 直接返回                          |
| 典型场景       | 传统的页面渲染（前后端不分）           | RESTful API 接口                  |

---

## 二、`@Controller` 返回值默认作为视图名

当 `@Controller` 中的处理方法返回 `String` 时，框架默认将其视为**逻辑视图名**（logical view name），不会直接作为 HTTP 响应内容。

### 完整流程

```
浏览器请求 /user
    │
    ▼
DispatcherServlet 接收请求
    │
    ▼
HandlerMapping 找到对应 Controller 方法
    │
    ▼
方法执行，返回 "user"（一个字符串）
    │
    ▼
ViewResolver 将 "user" 解析为真实视图路径
    │
    ├─ prefix + 逻辑视图名 + suffix
    │   /WEB-INF/views/ + "user" + .jsp
    │   = /WEB-INF/views/user.jsp
    │
    ▼
找到 user.jsp，渲染模板（注入 Model 中的数据）
    │
    ▼
生成纯 HTML，返回给浏览器
```

### 代码示例

```java
@Controller
public class UserController {

    @GetMapping("/user")
    public String showUser(Model model) {
        // ① 业务逻辑
        User user = userService.findById(1L);

        // ② 把数据放进 Model（传递给视图）
        model.addAttribute("user", user);

        // ③ 返回"视图名"——不是 JSON，不是 HTML
        // 框架拿到 "user" 后去找 user.jsp
        return "user";
    }
}
```

对应的 JSP 视图文件：

```jsp
<!-- /WEB-INF/views/user.jsp -->
<!DOCTYPE html>
<html>
<head><title>用户信息</title></head>
<body>
    <h1>用户详情</h1>
    <!-- 用 EL 表达式读取 Model 中的数据 -->
    <p>姓名：${user.name}</p>
    <p>年龄：${user.age}</p>
</body>
</html>
```

---

## 三、视图路径（View Path）与视图解析器

### 什么是视图路径？

视图路径是 ViewResolver 将**逻辑视图名**映射后得到的**真实文件位置**。

| 概念 | 示例 | 说明 |
|------|------|------|
| 逻辑视图名 | `"user"` | Controller 返回的字符串，不带路径和后缀 |
| 视图路径 | `/WEB-INF/views/user.jsp` | 实际的物理文件路径 |
| ViewResolver | `InternalResourceViewResolver` | 负责把前者映射为后者的组件 |

### 视图解析配置

Spring Boot 默认通过 `application.properties` / `application.yml` 配置前缀和后缀：

```yaml
spring:
  mvc:
    view:
      prefix: /WEB-INF/views/    # 前缀
      suffix: .jsp               # 后缀
```

映射公式：

```
逻辑视图名 "user"
  → prefix + 逻辑视图名 + suffix
  → /WEB-INF/views/ + "user" + .jsp
  → /WEB-INF/views/user.jsp  ← 这就是视图路径
```

### 为什么用 `/WEB-INF/`？

`/WEB-INF/` 目录在 Tomcat 等容器中**外部无法直接访问**。用户不能通过 `http://host/user.jsp` 直接打开页面，必须经过 Controller → 渲染 JSP 的完整流程。这起到一层**安全隔离**：所有页面展示都必须经过后端控制。

---

## 四、JSP 页面是什么？

> [!abstract] JSP — JavaServer Pages（Java 服务器页面）
> 一种服务端页面模板技术，本质是**嵌入了 Java 代码的 HTML 文件**。由 Servlet 容器在服务端渲染，将模板中的数据替换后生成纯 HTML 返回给浏览器。

### 通俗理解

JSP 就像一个**填空模板**：
- Java（Controller + Service）负责算出答案
- JSP 模板负责排版，把答案填进对应位置
- 最终输出一份完整的 HTML 给浏览器

### 示例对比

```jsp
<!-- 渲染前的 JSP 模板 -->
<p>姓名：${user.name}</p>    <!-- ${...} 是 EL 表达式，占位符 -->

<!-- 渲染后的 HTML（浏览器收到的） -->
<p>姓名：张三</p>             <!-- 占位符被替换成真实数据 -->
```

### 核心语法

| 语法 | 含义 | 示例 |
|------|------|------|
| `${...}` | EL 表达式，读取数据 | `${user.name}` |
| `<% ... %>` | 嵌入 Java 代码（不推荐） | `<% if (user != null) { %>` |
| `<%@ ... %>` | 页面指令 | `<%@ page contentType="text/html" %>` |
| JSTL 标签 | JSP 标准标签库 | `<c:forEach items="${list}" var="item">` |

---

## 五、`@RestController` 怎么处理返回值

`@RestController` 相当于在类上统一加了 `@ResponseBody`，所有方法的返回值**不经过视图解析**，而是通过 **HttpMessageConverter** 直接序列化为 JSON/XML 等格式写入 HTTP 响应体。

```java
@RestController
public class UserController {

    @GetMapping("/user")
    public User showUser() {
        // 返回值直接序列化为 JSON，写入响应体
        // 浏览器收到：{"name":"张三","age":25}
        return userService.findById(1L);
    }
}
```

流程对比：

```
@Controller 返回 String  "user"
    → ViewResolver 解析为 JSP 路径 → 渲染 HTML → 返回页面

@RestController 返回 User 对象
    → HttpMessageConverter 序列化 → {"name":"张三","age":25} → 返回 JSON
```

---

## 六、`@Controller` 返回 JSON 的场景

`@Controller` 也可以返回 JSON，但需要**在方法上显式加 `@ResponseBody`**：

```java
@Controller
public class UserController {

    // 加 @ResponseBody → 返回值直接写入响应体（序列化为 JSON）
    @GetMapping("/user")
    @ResponseBody
    public User showUser() {
        return userService.findById(1L);
    }

    // 不加 → 返回值当作视图名
    @GetMapping("/user-page")
    public String showUserPage(Model model) {
        model.addAttribute("user", userService.findById(1L));
        return "user";  // 去渲染 user.jsp
    }
}
```

---

## 七、面试话术

### 怎么向面试官解释两者的区别？

> "`@Controller` 是 Spring MVC 早期提供的注解，它的设计初衷是返回视图（JSP/Thymeleaf 等），返回值默认会被 ViewResolver 当成逻辑视图名去解析。如果我们想让它返回 JSON 数据，需要额外加 `@ResponseBody`。
>
> `@RestController` 是 Spring Boot 时代引入的，等于 `@Controller` + `@ResponseBody` 的组合，类上所有方法默认返回数据体，不再走视图解析，专为前后端分离的 RESTful API 设计。"

### 为什么 `@Controller` 不默认返回 JSON？

> "这是历史原因。Spring MVC 最初的设计目标是服务于传统 Web 应用（页面渲染），视图解析是默认行为。随着前后端分离架构流行，JSON 接口成为主流，所以才有了 `@RestController` 这个快捷方式。"

---

## 八、高频面试题

### Q1: @Controller 返回 String 时，框架如何知道去渲染哪个视图？

框架通过 **ViewResolver（视图解析器）** 完成映射。返回的 String 被视为**逻辑视图名**，ViewResolver 根据配置的前缀和后缀拼接出真实路径，然后交给 View 渲染。

```
返回 "user"
  → prefix (/WEB-INF/views/) + "user" + suffix (.jsp)
  → /WEB-INF/views/user.jsp
  → InternalResourceView 渲染后输出 HTML
```

**关键点：** 逻辑视图名不包含路径和后缀，由 ViewResolver 统一拼接。如果返回 `/WEB-INF/views/user.jsp` 这样的完整路径，反而可能被当作无效的逻辑视图名。

---

### Q2: 如何自定义 ViewResolver？如果有多个，优先级怎么处理？

**自定义方式：** 继承 `ViewResolver` 接口实现 `resolveViewName()`，或配置 `InternalResourceViewResolver` 并设置 `prefix`、`suffix`。

**多 ViewResolver 的优先级：** 当注册了多个 ViewResolver 时，按 `Ordered` 接口的 `order` 值从小到大依次尝试，第一个能解析成功的就使用。Spring 提供了 `ContentNegotiatingViewResolver` 作为顶层代理，它会先根据请求的 `Accept` 头或 URL 后缀判断内容类型，再委托给下层合适的 ViewResolver。

**面试话术：**

> "多个 ViewResolver 通过 `order` 属性控制优先级，值越小越先被尝试。`ContentNegotiatingViewResolver` 是最常用的顶层代理，它根据请求的 `Accept` 头、URL 后缀或参数来决定返回 JSON 还是 HTML，再委派给对应的子 ViewResolver。"

---

### Q3: forward 和 redirect 的区别？在 Spring MVC 中怎么写？

#### 核心机制对比

两者本质上是 **Servlet 容器层面的两种请求流转方式**，Spring MVC 通过视图名前缀来切换：

| 维度 | forward（转发） | redirect（重定向） |
|------|----------------|------------------|
| 请求次数 | 1 次 | 2 次 |
| URL 变化 | 浏览器地址栏不变 | 浏览器地址栏变为目标 URL |
| 数据共享 | 共享同一个 HttpServletRequest，Request Scope 数据全部保留 | 新请求，原 Request 销毁，所有 Request Scope 数据丢失 |
| 性能 | 略好（少一次网络往返） | 略差（多一次 HTTP 往返） |
| 适用场景 | 内部页面跳转、MVC 流程中"委托给另一个视图" | 提交后跳转（PRG 模式）、跨 Controller 跳转、需要"刷新不重复提交"的场景 |

#### 底层原理

**forward 的流程：**

```
浏览器 → POST /submit
         │
         ▼
    DispatcherServlet
         │
         ▼
    Controller 返回 "forward:/result"
         │
         ▼
    RequestDispatcher.forward()  ← 服务器内部转发
         │
         ▼
    /result 对应的 Controller / 视图
         │
         ▼
    浏览器 ← 最终响应（仅一次请求/响应）
```

- `forward` 调用的是 `ServletRequest.getRequestDispatcher().forward()`
- 整个过程在**同一个请求线程**中完成，浏览器只收到一次响应
- 请求头、请求参数、Request Attribute 全部保留

**redirect 的流程：**

```
浏览器 → POST /submit
         │
         ▼
    DispatcherServlet
         │
         ▼
    Controller 返回 "redirect:/result"
         │
         ▼
    响应头：HTTP 302 + Location: /result
         │
         ▼
    浏览器 ← 收到 302，自动发起 GET /result（第二次请求）
         │
         ▼
    DispatcherServlet → /result 对应的 Controller
         │
         ▼
    浏览器 ← 收到最终响应
```

- `redirect` 返回的是 HTTP 302 状态码 + `Location` 响应头
- 浏览器**自动发起第二次请求**，是一个全新的 HttpServletRequest
- URL 变为 `/result`，原 `/submit` 的 Request 已销毁

#### Spring MVC 中的写法

```java
@Controller
public class OrderController {

    // ── forward：服务器内部转发 ──

    // 场景：当前方法处理完后，交给另一个 Controller 方法继续处理
    // 请求路径仍为 /process，但内部转发到 /handle
    @PostMapping("/process")
    public String process(Order order, Model model) {
        orderService.validate(order);
        // forward：把当前请求"移交"给 /handle，Request 数据全部保留
        return "forward:/handle";
    }

    @PostMapping("/handle")
    public String handle(@ModelAttribute Order order) {
        // 这里能取到 /process 中放入 Model 的数据
        return "order-success";
    }

    // forward 到具体视图路径（跳过视图解析器）
    @GetMapping("/order/list")
    public String list(Model model) {
        model.addAttribute("orders", orderService.findAll());
        // forward: 直接转发到 JSP，不走 ViewResolver
        return "forward:/WEB-INF/views/order-list.jsp";
    }


    // ── redirect：重定向到新 URL ──

    // 场景一：创建成功后跳转到列表页
    @PostMapping("/orders")
    public String create(@ModelAttribute Order order) {
        Long id = orderService.create(order);
        // 302 跳转，浏览器地址变为 /orders
        return "redirect:/orders";
    }

    // 场景二：带路径参数的 redirect
    @PostMapping("/orders/{id}/cancel")
    public String cancel(@PathVariable Long id) {
        orderService.cancel(id);
        // 路径变量可直接拼入 URL
        return "redirect:/orders/" + id;
    }

    // 场景三：带查询参数的 redirect
    @GetMapping("/search")
    public String search(@RequestParam String keyword, Model model) {
        List<Order> results = orderService.search(keyword);
        model.addAttribute("keyword", keyword);
        model.addAttribute("results", results);
        // ⚠️ 注意：redirect 后 Model 数据丢失！
        // 下方这行代码中 model 的数据无法传递到 /search-result
        return "redirect:/search-result";
        // 正确做法见下方 RedirectAttributes
    }
```

#### RedirectAttributes：重定向传参的正确姿势

`redirect` 后 Request Scope 数据丢失，Spring 提供了 **Flash Map** 机制解决此问题：

> [!info] Session 是什么？
> HTTP 本身是无状态协议——每次请求服务器都不记得你是谁。Session（HttpSession）是 Servlet 规范提供的服务端会话机制：服务器为每个用户生成一个带唯一 ID 的内存空间，通过 Cookie（JSESSIONID）发给浏览器，后续浏览器自动携带这个 ID，服务器就能认出"这是同一个人"。两次 HTTP 请求之间唯一能跨请求存数据的地方就是 Session。

```java
@Controller
public class OrderController {

    // ── RedirectAttributes ──

    @PostMapping("/orders")
    public String create(@ModelAttribute Order order, RedirectAttributes redirectAttrs) {
        Long id = orderService.create(order);

        // addFlashAttribute → 数据存入 Flash Map（存在 Session 中）
        // 重定向后自动读取，取一次即清除，不会残留在 URL 中
        redirectAttrs.addFlashAttribute("orderId", id);
        redirectAttrs.addFlashAttribute("message", "订单创建成功");

        return "redirect:/orders/" + id;
    }

    @GetMapping("/orders/{id}")
    public String detail(@PathVariable Long id, Model model,
                         @ModelAttribute("message") String message) {
        // @ModelAttribute 自动从 Flash Map 中取出 "message"
        // 第一次访问（来自 redirect）→ 取到 message
        // 用户手动刷新页面  → Flash Map 已清空，message 为 null
        model.addAttribute("order", orderService.findById(id));
        model.addAttribute("msg", message);
        return "order-detail";
    }
```

**Flash Map 生命周期：**

```
1. Controller 调用 addFlashAttribute("msg", "成功")
   → 数据存入 FlashMap（放在 Session 中）

2. 返回 "redirect:/result"
   → 响应 302 + Location: /result

3. 浏览器发起 GET /result（新请求）

4. DispatcherServlet 检测到 Session 中有 FlashMap
   → 自动将数据转移到目标请求的 Model 中
   → FlashMap 从 Session 中移除（只取一次）

5. 目标 Controller 通过 @ModelAttribute 取到数据
```

| 方法 | 说明 |
|------|------|
| `addFlashAttribute(String name, Object value)` | 存入 Flash Map，重定向后自动读取并清除 |
| `addAttribute(String name, Object value)` | 同 Model.addAttribute，但 redirect 时自动拼到 URL 参数中 |

> [!tip] Flash Map vs URL 参数
> - **URL 参数**（`addAttribute`）：数据暴露在浏览器地址栏，适合非敏感数据
> - **Flash Map**（`addFlashAttribute`）：数据存在 Session，不暴露在 URL，适合敏感数据或复杂对象

#### 面试场景题：什么时候用 forward，什么时候用 redirect？

**面试话术：**

> "选择 forward 还是 redirect，核心看三个因素：
>
> 第一，**是否需要保持 Request Scope 数据**。如果需要共享同一个请求中的数据（比如校验时的错误信息、Form 数据），用 forward。
>
> 第二，**是否涉及表单提交**。提交后必须用 redirect，即 PRG 模式。下面详细展开。

##### PRG（Post-Redirect-Get）模式详解

**问题：不用 PRG 会怎样？**

假设一个下单接口用 forward 返回结果页：

```
场景：用户提交订单

① 浏览器 POST /orders/create
         │
         ▼
② Controller 处理（创建订单、扣库存）
         │
         ▼
③ forward → 返回结果页 /order-success
         │
         ▼
④ 浏览器显示"下单成功"页面
         │
         ▼
⑤ 用户按 F5 刷新页面
         │
         ▼
⑥ ⚠️ 浏览器重新发送上一次的请求：POST /orders/create
         │
         ▼
⑦ ⚠️ 订单被重复创建，库存被重复扣减
```

**根本原因：** 用户刷新时，浏览器会对**当前地址栏的 URL 对应的最后一个请求**做重放。如果地址栏是 `/order-success` 而它是 forward 来的（没有独立的 HTTP 响应），浏览器会回退到上一个请求——也就是那个 POST。

**用了 PRG 之后：**

```
场景：用户提交订单

① 浏览器 POST /orders/create
         │
         ▼
② Controller 处理（创建订单、扣库存）
         │
         ▼
③ redirect → 302 → 浏览器地址变为 /orders/123
         │
         ▼
④ 浏览器自动发起 GET /orders/123
         │
         ▼
⑤ 浏览器显示"下单成功"页面（GET 请求的结果）
         │
         ▼
⑥ 用户按 F5 刷新页面
         │
         ▼
✅ 浏览器重新发送 GET /orders/123（安全的读请求）
   不会再触发下单逻辑
```

**关键区别：** 地址栏的 URL 是 `GET /orders/123`，刷新只重复 GET，不会重复 POST。

##### 其他重复提交场景

PRG 解决的不仅限于手动刷新，还包括：

| 场景       | 不使用 PRG 的问题  | PRG 解决方式         |
| -------- | ------------ | ---------------- |
| 用户手动刷新   | 重复 POST 提交   | 刷新的是 GET 请求，安全   |
| 浏览器前进/后退 | 后退再前进可能重新提交  | 前进到的是 GET 结果页    |
| 用户快速双击按钮 | 短时间内发两个 POST | 配合前端防抖 + 后端的幂等校验 |

> [!warning] PRG 不是银弹
> PRG 解决了"用户无意的重复请求"，但**无法防止恶意用户或竞态条件导致的重复提交**。生产环境必须配合：
> - **前端**：按钮防抖（提交后置灰）
> - **后端**：接口幂等（用订单号做唯一键、分布式锁、Token 机制）
> - **数据库**：唯一索引兜底

##### PRG 的标准代码写法

```java
@Controller
public class OrderController {

    // ✅ PRG 模式：POST → redirect → GET
    @PostMapping("/orders")
    public String create(@ModelAttribute Order order, RedirectAttributes redirectAttrs) {
        Long id = orderService.create(order);

        // 传递成功提示（Flash Map，不暴露在 URL）
        redirectAttrs.addFlashAttribute("message", "下单成功，订单号：" + id);

        // 重定向到详情页（GET 请求）
        return "redirect:/orders/" + id;
    }

    @GetMapping("/orders/{id}")
    public String detail(@PathVariable Long id, Model model,
                         @ModelAttribute("message") String message) {
        model.addAttribute("order", orderService.findById(id));
        model.addAttribute("msg", message);
        return "order-detail";   // 用户刷新这个 GET 请求，安全
    }
```
>
> 第三，**是否跨 Controller**。如果目标 URL 的处理逻辑在另一个 Controller 类中，建议用 redirect。下面详细展开。

##### 什么是"跨 Controller"？

**同一个 Controller 内部：** 目标 URL 的处理方法在当前类中。

```java
// UserController 内部跳转
@Controller
public class UserController {

    @PostMapping("/users")
    public String create(User user) {
        // → 目标是本类的 success() 方法
        return "forward:/users/success";     // ✅ 不跨 Controller
    }

    @GetMapping("/users/success")
    public String success() { return "create-success"; }
}
```

**跨 Controller：** 目标 URL 由另一个 Controller 类处理。

```java
// OrderController → 跳到 UserController
@Controller
public class OrderController {

    @PostMapping("/orders")
    public String create(Order order) {
        // → 目标是 UserController 的 detail() 方法
        return "redirect:/users/1";          // ⚠️ 跨 Controller
    }
}

@Controller
public class UserController {

    @GetMapping("/users/{id}")
    public String detail(@PathVariable Long id) { ... }
}
```

##### 为什么跨 Controller 建议用 redirect？

| 维度 | forward | redirect |
|------|---------|----------|
| 浏览器地址栏 | 仍显示当前 URL（如 `/orders`） | 变为真实目标 URL（如 `/users/1`） |
| URL 与逻辑的对应关系 | ❌ 地址 ≠ 实际处理逻辑 | ✅ 地址 = 实际处理逻辑 |
| 用户收藏/书签 | 收藏的是当前 URL，下次打开不是目标页面 | 收藏的就是目标 URL，直接可访问 |
| 前后端分离场景 | 不适用（后端只返回 JSON） | 天然契合（接口独立、URL 明确） |

```java
// ❌ 跨 Controller 用 forward：地址栏混乱
// 用户在浏览器看到 /orders，实际页面是 UserController 渲染的
return "forward:/users/1";

// ✅ 跨 Controller 用 redirect：地址栏与实际页面一致
// 用户在浏览器看到 /users/1，实际也是 UserController 处理的
return "redirect:/users/1";
```

**面试话术：**

> "同一个 Controller 内部跳转可以用 forward，速度快、数据共享方便。但跨到另一个 Controller 时，forward 会导致浏览器地址栏和实际页面不一致——用户看到的 URL 是发起请求的那个，而不是目标页面的。redirect 会让浏览器地址栏变成真实的 URL，保持 URL 和处理逻辑的一一对应，也方便用户收藏书签。前后端分离架构下 API 天然是独立的，基本都是 redirect 的思路。"

---

#### 口述回答

下面这部分可以连贯地说给面试官：

> "forward 和 redirect 选哪个，我一般看三个情况。
>
> 第一个，**要不要保留请求中的数据**。forward 是同一个请求，Model 里的数据直接就能拿到，适合内部跳转传数据。redirect 是两次请求，原 Request 销毁了，数据全部丢失，如果硬要传就得用 RedirectAttributes 的 Flash Map——数据存在 Session 里，取一次就删。
>
> 第二个，**有没有表单提交**。这个特别重要。提交之后必须走 redirect，也就是 PRG 模式。为什么呢？如果不走 PRG，用户提交后 forward 到结果页，这时候地址栏里的 URL 实际上还是上一次的 POST 请求。用户按 F5 刷新，浏览器会重新发那个 POST，导致订单重复创建、库存重复扣减。走 PRG 的话，POST 之后先 302 跳转，浏览器地址变成一个新的 GET 请求，刷新也只是重复 GET，安全。
>
> 第三个，**跳转目标在不在同一个 Controller 里**。同一个类内部 forward 就行，快，数据也方便传。但如果目标是另一个 Controller，建议 redirect——forward 的话浏览器地址栏显示的还是发起请求的那个 URL，用户看到的地址和实际页面不一致，收藏夹也收不到正确的页面。redirect 之后地址栏变成真实 URL，URL 和处理逻辑是一一对应的。
>
> 总结一下就是：**内部跳转用 forward，表单提交必用 redirect 做 PRG，跨 Controller 也用 redirect。**"

#### 常见面试追问

**追问 1：forward 和 redirect 在 Spring MVC 中是怎么实现的？**

> [!info] 完整调用链：从返回值到 HTTP 响应

整个流程涉及 5 个关键组件，每一步都参与判断：

```
Controller 返回 "forward:/users/success"
         │
         ▼
① HandlerMethodReturnValueHandler
   检测到返回值是 String 类型
         │
         ▼
② ViewNameMethodReturnValueHandler
   判断 String 是否以 "forward:" 或 "redirect:" 开头
         │
         ├─ 以 "forward:" 开头 → 创建 InternalResourceView
         └─ 以 "redirect:" 开头 → 创建 RedirectView
         │
         ▼
③ ViewResolver（或直接由 View 处理）
   如果是 InternalResourceView → 调用 RequestDispatcher.forward()
   如果是 RedirectView       → 设置 302 + Location 头
         │
         ▼
④ HttpServletResponse
   forward  → 直接写入响应体（HTML），状态码由目标资源决定
   redirect → 状态码 302，响应头 Location，响应体为空
         │
         ▼
⑤ 浏览器
   forward  → 收到 HTML，直接渲染，地址栏不变
   redirect → 收到 302 + Location，自动发起第二次请求
```

---

##### 详细源码流程

**第一步：返回值处理（HandlerMethodReturnValueHandler）**

DispatcherServlet 调用 `HandlerAdapter.handle()` 执行 Controller 方法后，拿到返回值 `"forward:/users/success"`。接下来进入 `HandlerMethodReturnValueHandler` 体系：

- Spring MVC 按顺序遍历所有 `HandlerMethodReturnValueHandler`
- `ModelMethodProcessor` → 不处理 String
- `ViewNameMethodReturnValueHandler` → 命中！返回值是 String

**第二步：视图名解析（ViewNameMethodReturnValueHandler）**

```java
// ViewNameMethodReturnValueHandler.handleReturnValue() 简化逻辑
public void handleReturnValue(Object returnValue, ...) {
    String viewName = (String) returnValue;

    // 核心判断：是否有前缀
    if (viewName.startsWith(REDIRECT_URL_PREFIX)) {   // "redirect:"
        // 去掉前缀，得到目标路径
        String target = viewName.substring(REDIRECT_URL_PREFIX.length());
        // 创建 RedirectView
        view = new RedirectView(target);
    }
    else if (viewName.startsWith(FORWARD_URL_PREFIX)) { // "forward:"
        // 去掉前缀，得到目标路径
        String target = viewName.substring(FORWARD_URL_PREFIX.length());
        // 创建 InternalResourceView
        view = new InternalResourceView(target);
    }
    else {
        // 普通逻辑视图名 → 交给 ViewResolver 解析
        view = resolveViewName(viewName, ...);
    }

    // 渲染视图
    render(view, model, request, response);
}
```

**关键判断点：** `startsWith("forward:")` 和 `startsWith("redirect:")`——这两个前缀让 Spring 知道不走普通视图解析，直接走特殊转发/重定向逻辑。

---

**第三步（A）：forward 的渲染过程（InternalResourceView）**

```java
// InternalResourceView.renderMergedOutputModel() 简化逻辑
protected void renderMergedOutputModel(Map<String, Object> model,
        HttpServletRequest request, HttpServletResponse response) throws Exception {

    // 拼接 URL：解析 forward: 后面的路径
    String path = getUrl();  // 即 "/users/success"

    // 获取 RequestDispatcher（Servlet 容器提供）
    RequestDispatcher dispatcher = request.getRequestDispatcher(path);

    // 把 Model 中的数据放到 Request Attribute 中
    for (Map.Entry<String, Object> entry : model.entrySet()) {
        request.setAttribute(entry.getKey(), entry.getValue());
    }

    // ★ 核心操作：服务器内部转发
    dispatcher.forward(request, response);
    // 注意：没有 response.getWriter().write() 或 response.sendRedirect()
    // forward 之后，响应由目标资源（另一个 Servlet/JSP）来控制
}
```

**forward 的关键特征：**
- 调用 `RequestDispatcher.forward()`——这是 **Servlet 规范** 定义的方法
- 同一个请求线程，同一个 HttpServletRequest 对象
- 目标资源写响应，状态码由目标资源决定
- 响应体是目标资源生成的 HTML

---

**第三步（B）：redirect 的渲染过程（RedirectView）**

```java
// RedirectView.renderMergedOutputModel() 简化逻辑
protected void renderMergedOutputModel(Map<String, Object> model,
        HttpServletRequest request, HttpServletResponse response) throws IOException {

    // 拼接目标 URL
    String targetUrl = calculateURL(request, getUrl());
    // "/users/success" → 相对路径解析 → "http://localhost:8080/users/success"

    // ★ 核心操作：设置 HTTP 状态码 + 响应头
    response.setStatus(HttpServletResponse.SC_FOUND);  // 302
    response.setHeader("Location", targetUrl);
    // 注意：没有 response.getWriter().write()
    // 响应体是空的！靠状态码 + Location 头驱动浏览器跳转
}
```

**redirect 的关键特征：**
- 不调用 `RequestDispatcher`，也不调用 `response.sendRedirect()`
- 直接操作 `HttpServletResponse`：设置状态码 302 + `Location` 响应头
- 响应体为空
- 浏览器根据 `Location` 头**自动发起第二次请求**

---

##### 两者的继承关系

```
View（接口）
  ├── AbstractUrlBasedView
  │     ├── InternalResourceView     ← forward 用
  │     └── RedirectView             ← redirect 用
  └── ThymeleafView / JstlView 等    ← 普通视图渲染
```

- `InternalResourceView` 继承链最终调用 `RequestDispatcher.forward()`——**Servlet 容器层面**的操作
- `RedirectView` 直接操作 HTTP 响应头——**HTTP 协议层面**的操作

---

##### 面试话术

> "具体实现分两种情况。forward 走的是 `InternalResourceView`，底层调用的是 `RequestDispatcher.forward()`，这是 Servlet 规范的方法，在服务器内部把请求"移交"给目标资源，同一个请求线程，不经过网络。redirect 走的是 `RedirectView`，底层不调用 Servlet API，直接往响应里写 HTTP 302 状态码和 Location 头，响应体为空，剩下的全靠浏览器自己去跳转。两者虽然都是 View 接口的子类，但做的事情完全不同——一个是服务器内部移交，一个是靠协议头驱动浏览器。"

**追问 2：forward 能跨域吗？**

> 不能。`forward` 是服务器内部操作，只能在同一个 Web 应用内部进行，不能转发到其他域名的 URL。`redirect` 可以跨域，因为最终是浏览器发起的 HTTP 请求。

**追问 3：redirect 默认是 302，什么时候用 301？**

> Spring MVC 的 `redirect:` 前缀默认返回 **302（临时重定向）**。如果需要永久重定向，可以用 `RedirectView` 手动设置状态码为 301，但 301 会被浏览器缓存，不适用于 POST 提交后的跳转场景。

---

### Q4: Model、ModelMap、ModelAndView 有什么区别？

三者都是用来传递数据给视图的机制，本质相同，使用方式略有差异：

| 类型 | 说明 | 使用方式 |
|------|------|----------|
| `Model` | 最顶层接口，只提供数据存放 | 方法参数直接注入 |
| `ModelMap` | `LinkedHashMap` 的子类，实现 Model | 用法和 Model 完全一致，额外提供 Map 方法 |
| `ModelAndView` | 同时携带 Model 数据和 View 信息 | 作为返回值，包含 `addObject()` + `setViewName()` |

```java
// Model — 最简洁
@GetMapping("/user")
public String getUser(Model model) {
    model.addAttribute("user", user);
    return "user";
}

// ModelAndView — 数据+视图名一体返回
@GetMapping("/user")
public ModelAndView getUser() {
    ModelAndView mv = new ModelAndView();
    mv.addObject("user", user);
    mv.setViewName("user");
    return mv;
}
```

**面试话术：**

> "三者的底层都是同一个 Map，只是 API 封装方式不同。日常开发推荐用 Model（方法参数注入），最简洁。如果需要同时返回数据和视图名但不想用 String 返回值，才用 ModelAndView。"

---

### Q5: @RequestMapping 有哪些常用属性？

```java
@RequestMapping(
    value = "/users",           // URL 路径，支持 Ant 风格 /users/{id}
    method = RequestMethod.GET, // 限制 HTTP 方法
    params = "id",              // 要求请求必须包含 id 参数
    headers = "X-Requested-With=XMLHttpRequest", // 限制请求头
    consumes = "application/json",  // 限制 Content-Type
    produces = "application/json"   // 限制 Accept 返回类型
)
```

- `GET` / `POST` / `PUT` / `DELETE` 分别有快捷注解：`@GetMapping`、`@PostMapping`、`@PutMapping`、`@DeleteMapping`
- `params` 和 `headers` 支持否定：`params = "!id"` 表示请求不能包含 id 参数

---

### Q6: RESTful 风格是什么？Spring MVC 中如何支持？

**RESTful** 是一种架构风格，核心约定：

| 操作 | HTTP 方法 | URL 示例 |
|------|-----------|----------|
| 查询列表 | GET | `/api/users` |
| 查询单个 | GET | `/api/users/{id}` |
| 创建 | POST | `/api/users` |
| 更新 | PUT | `/api/users/{id}` |
| 删除 | DELETE | `/api/users/{id}` |

**Spring MVC 中的实现：**

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping                    // GET /api/users
    public List<User> list() { ... }

    @GetMapping("/{id}")           // GET /api/users/1
    public User get(@PathVariable Long id) { ... }

    @PostMapping                   // POST /api/users
    public User create(@RequestBody User user) { ... }

    @PutMapping("/{id}")           // PUT /api/users/1
    public User update(@PathVariable Long id, @RequestBody User user) { ... }

    @DeleteMapping("/{id}")        // DELETE /api/users/1
    public void delete(@PathVariable Long id) { ... }
}
```

---

### Q7: @PathVariable 和 @RequestParam 有什么区别？

| 注解 | 来源 | 示例 | 适用场景 |
|------|------|------|----------|
| `@PathVariable` | URL 路径中的变量 | `/users/{id}` → `@PathVariable("id")` | RESTful 风格路由 |
| `@RequestParam` | 查询参数或表单参数 | `/users?id=1` → `@RequestParam("id")` | 传统查询参数 |

```java
// URL: /users/100 → id = 100
@GetMapping("/users/{id}")
public User getUser(@PathVariable Long id) { ... }

// URL: /users?id=100 → id = 100
@GetMapping("/users")
public User getUser(@RequestParam Long id) { ... }
```

`@RequestParam` 的 `required` 属性默认 `true`，参数缺失会抛异常。设为 `false` 或配合 `defaultValue` 可实现可选参数。

---

### Q8: 为什么推荐用 Thymeleaf 替代 JSP？

| 维度 | JSP | Thymeleaf |
|------|-----|-----------|
| 渲染方式 | 编译为 Servlet，需容器启动 | 直接解析 HTML 模板，无需容器 |
| 浏览器预览 | JSP 标签未替换，页面混乱 | 纯 HTML 文件，浏览器可直接打开 |
| Spring Boot 支持 | 有限，需额外配置 | 开箱即用 |
| 语法 | JSP 标签 + EL 表达式 | 类 HTML 的自然模板语法 `th:text` |

```html
<!-- JSP -->
<p>姓名：${user.name}</p>

<!-- Thymeleaf -->
<p th:text="${user.name}">姓名</p>
```

JSP 在 Spring Boot 中不是默认视图方案，使用 `spring-boot-starter-thymeleaf` 即可集成。

**口述回答：**

> "JSP 的问题主要在三点。
>
> 第一，**JSP 本质是 Servlet**，需要在 Tomcat 这样的容器里编译运行，不能独立预览。前端开发人员打开 JSP 文件看到的是满屏的标签和乱码，没法直接看效果，前后端联调效率很低。而 Thymeleaf 的模板就是纯 HTML 文件，浏览器直接就能打开，前端不用装任何东西就能看页面原型。
>
> 第二，**Spring Boot 默认不支持 JSP**。Spring Boot 内嵌的是 Tomcat，但 JSP 的编译依赖 Jasper 引擎，需要额外配置。而 Thymeleaf 有官方 starter，引入一个依赖就能用，开箱即用。
>
> 第三，**JSP 的语法太老了**。JSP 里可以嵌入 Java 代码 `<% %>`，虽然现在不推荐，但语法本身允许，容易写出混杂的"意大利面"代码。Thymeleaf 是纯属性式模板，`th:text`、`th:each`，逻辑清晰，和 HTML 结构自然融合。
>
> 当然如果是在传统 SSH 架构（Structs2 + Spring + Hibernate）的老项目里，JSP 还是主流，不是说 JSP 不好，只是在新项目和 Spring Boot 生态下 Thymeleaf 是更好的选择。"

---

### Q9: 重定向时 Model 数据丢失怎么办？如何传递参数？

`redirect` 是新的 HTTP 请求，原 Request Scope 的数据全部丢失。三种解决方案：

```java
@PostMapping("/submit")
public String submit(User user, RedirectAttributes redirectAttrs) {
    // 方案一：RedirectAttributes → 放在 Flash Map（Session 级别，取一次即清除）
    redirectAttrs.addFlashAttribute("msg", "提交成功");
    return "redirect:/result";

    // 方案二：URL 参数拼接（简单但暴露数据）
    return "redirect:/result?name=" + user.getName();

    // 方案三：Session（手动管理，不推荐）
    request.getSession().setAttribute("msg", "提交成功");
}
```

> [!tip] addFlashAttribute
> Flash Map 存在 Session 中，重定向后自动读取并清除，一次有效，不会残留在 URL 中。

---

### Q10: ContentNegotiatingViewResolver 解决什么问题？

当同一个接口需要根据请求头（`Accept`）或 URL 后缀返回**不同格式**（HTML / JSON / XML）时，用它作为顶层 ViewResolver：

```
请求: GET /api/users/1  Accept: application/json
  → ContentNegotiatingViewResolver
  → MappingJackson2HttpMessageConverter
  → {"id":1,"name":"张三"}

请求: GET /api/users/1  Accept: text/html
  → ContentNegotiatingViewResolver
  → ThymeleafViewResolver
  → 渲染 HTML 页面
```

这样同一个 URL 可以提供多视图格式，无需写多个接口。

**口述回答：**

> "ContentNegotiatingViewResolver 解决的问题是：同一个接口路径，前端可能想要不同格式的返回——比如浏览器要 HTML 页面，移动端 App 要 JSON，第三方系统要 XML。如果每个格式都写一个接口，代码会大量重复。
>
> 它的工作方式其实很简单：它自己不渲染任何内容，只做一个"分发员"。它先看请求里带了什么，主要看三个地方——URL 后缀、请求头 Accept、请求参数 format。比如 URL 是 `/api/users/1.json` 就看后缀，请求头是 `Accept: application/json` 就看 Accept 头。
>
> 判断出客户端要什么格式之后，它把请求转交给下层对应的 ViewResolver 或 HttpMessageConverter 去处理。要 JSON 就交给 Jackson 序列化，要 HTML 就交给 ThymeleafViewResolver 渲染模板，要 XML 就交给 XML 的转换器。
>
> 所以在代码层面，Controller 只需要写一个方法，返回一个对象，根据请求的不同自动返回不同格式。配合 `@RequestMapping(produces = ...)` 或者 URL 后缀就能实现。不过实际项目里，前后端分离架构下接口基本都是 JSON，这个组件更多是用在需要同时提供页面和 API 的混合架构里。"

---

## 九、总结对比图

```
请求进来
  │
  ├─ @Controller ──────────────────────────────────┐
  │   返回 String "user"                             │
  │   → ViewResolver 解析                            │
  │   → 找到 user.jsp                                │
  │   → 渲染 HTML → 浏览器看到页面                    │
  │                                                  │
  ├─ @Controller + @ResponseBody ──────────────────┤
  │   返回 User 对象                                 │
  │   → HttpMessageConverter 序列化为 JSON           │
  │   → 浏览器收到 {"name":"张三"}                    │
  │                                                  │
  ├─ @RestController ───────────────────────────────┤
  │   返回 User 对象                                 │
  │   → 同上，直接返回 JSON                          │
  └──────────────────────────────────────────────────┘
```

---

## 关联知识点

- [[SpringMVC]] — 核心注解与参数绑定的完整说明
- [[SpringBoot]] — @RestController 主要应用场景
- [[IOC容器]] — DispatcherServlet 的初始化与Bean管理
