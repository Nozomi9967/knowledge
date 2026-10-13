**URI** (Uniform Resource Identifier) 和 **URL** (Uniform Resource Locator) 的关系：

**URL 是 URI 的子集。**

URI 是统一资源标识符，用于唯一标识一个资源。它有两种形式：

- **URL**（定位符）：通过"位置"来标识资源，告诉你去哪里找它
- **URN**（统一资源名称）：通过"名字"来标识资源，不关心位置

------

**直观类比：**

把资源比作一个人：

- URN 相当于身份证号：`urn:isbn:978-7-111-21558-6`（唯一，但不告诉你去哪找）
- URL 相当于住址：`https://example.com/book/123`（告诉你去哪找）
- URI 是上位概念，两者都是 URI

------

**日常开发中：**

实际上 URN 极少用，几乎所有 URI 都是 URL，所以两个词在日常口语里经常混用，不必过于纠结。

Spring MVC 的 `@RequestMapping` 注解里的路径，严格来说叫 URI（只有路径部分，没有协议和域名），但大家也常说 URL，都能理解。