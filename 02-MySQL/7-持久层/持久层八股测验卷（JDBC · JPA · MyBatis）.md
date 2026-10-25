---
tags:
  - 持久层
  - 测验卷
  - jdbc
  - jpa
  - mybatis
created: 2026-08-11
updated: 2026-08-11
source: 出卷准则
---

# 持久层八股测验卷（JDBC · JPA · MyBatis）

> 答题说明：以下均为简答题，请尽量用自己的话阐述原理，回答完成后发给我评估。
> 范围：[[JDBC详解]] + [[JPA详解]] + [[MyBatis详解]]
> 规则：10 道简答题，均为面试常见问法。答的时候口头说答案即可，批改时会追问 2-3 层。

---

## 一、JDBC 基础（1-3 题）

1. JDBC 是什么？驱动是什么？为什么说「任何 ORM 的尽头都是 JDBC」？

2. Statement 和 PreparedStatement 有什么区别？为什么永远用 PreparedStatement？（举一个 SQL 注入的例子说明）

3. JDBC 操作数据库的完整流程是哪几步？为什么需要连接池？连接池里的连接和事务有什么关系？

---

## 二、JPA 三层关系（4-6 题）

4. JPA、Hibernate、Spring Data JPA 三者是什么关系？用类比说明。（顺带解释：为什么说 Hibernate 是 JPA 的超集？）

5. Spring Data JPA 的 Repository 接口继承树是什么？方法名 `findByNameAndAgeGreaterThan` 是怎么变成 SQL 的？方法名表达不了的复杂查询怎么办？（至少说两种方案）

6. Hibernate 的实体有哪四种状态？每种状态怎么进入、有什么特征？「改了没保存」的 bug 是哪个状态导致的？为什么？

---

## 三、MyBatis（7-8 题）

7. MyBatis 的一级缓存和二级缓存分别在什么范围？为什么说一级缓存是「同一个 SqlSession」级别的？二级缓存有什么坑？

8. MyBatis 和 JPA 的设计哲学有什么本质区别？各自的适用场景是什么？（用「以谁为中心」来回答）

---

## 四、综合串联（9-10 题）

9. 一条 SQL：从你的 Spring Boot 代码调用 MyBatis-Plus 的 `selectById` 开始，到 MySQL 返回结果，中间经过了哪些层？（把整条链路说出来）

10. 面试官问：「你项目用的 MyBatis-Plus，那你了解 JPA 吗？」你怎么回答？（考察的是你能否把两条路线的优劣讲清楚，而不是只背定义）

---

## 关联知识点

- [[JDBC详解]] — 卷一基础部分范围：五步流程、PreparedStatement、连接池、SPI
- [[JPA详解]] — 卷二三层关系部分范围：Repository 体系、实体状态机、懒加载
- [[MyBatis详解]] — 卷三 MyBatis 部分范围：缓存、SqlSession、设计哲学
- [[MyBatis-Plus详解]] — 综合串联题的项目背景：LambdaQueryWrapper、BaseMapper
- [[N+1查询问题详解]] — 综合题可能被追问的延伸考点
