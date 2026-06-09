# 模块文档模板

模块文档应帮助 Java 维护者理解职责边界、Spring 入口、核心类、数据流和依赖关系。只写 artifacts 或源码片段能够支持的内容。

## 通用规则

- 写入 `module_tree.json` 中的模块 `doc_path`。
- 一级标题使用模块 `name`。
- 链接使用相对当前 Markdown 文件的路径。
- 提到 Java 类型时优先使用 `qualified_name`，必要时补充 `file_path`。
- 提到接口、依赖、Maven 模块或组件时，必须能在 `modules/*.json`、`components/*.json`、`dependencies.json` 或 `analysis.json.build_system` 中找到依据。
- 不逐行复述源码。
- DTO、VO、枚举、请求对象、响应对象不要成为正文主体，除非它们决定核心流程。

## 叶子模块

推荐结构：

```markdown
# 模块名称

## 用途

说明模块在 Java/Maven/Spring 系统中的职责、边界和不负责的内容。

## Java 包与 Maven 模块

列出主要 package、物理 Maven module 和重要文件。

## Spring 组件

按 stereotype 总结 Controller、Service、Repository、Configuration、Component、远程客户端、消息处理器和任务处理器。

## 对外入口

如果 `entry_points` 非空，列出 REST endpoint、HTTP 方法、路径、控制器和处理方法。

## 核心类型

说明重要类和接口的职责、关键字段、关键方法、继承或实现关系。

## 主要流程

根据方法调用、注入关系和模块依赖描述关键请求、命令、事件或定时任务流程。

## 依赖关系

说明内部依赖、外部模块依赖和依赖类型，如 `constructor_injection`、`method_call`、`extends`、`implements`。

## 数据与配置

总结关键实体、聚合根、DTO 分组、配置属性和基础设施设置。

## 维护注意事项

记录事务、扩展点、错误处理、远程调用、消息行为、持久化风险或 analyzer 覆盖不足的地方。
```

只有当图表能提升理解时才使用 Mermaid。大模块优先使用一张聚焦图和少量表格。

如果叶子模块仍然偏大，说明它来自最终模块树，不要在文档阶段继续拆分。

## 父模块

推荐结构：

```markdown
# 模块名称

## 用途

说明该业务域或技术域的整体职责。

## 子模块

列出直接子模块，概括职责并链接文档。

## 架构关系

以直接子模块为节点，用 Mermaid 展示依赖方向。

## 跨模块流程

描述跨子模块的典型流程，例如 API -> Application -> Domain -> Persistence。

## 依赖边界

总结允许的依赖方向和实际观察到的依赖方向。

## 横切关注点

总结公共模型、配置、基础设施、远程调用、消息、缓存、安全或任务约定。
```

父模块必须保持概述级别：

- 不复制子模块组件列表。
- 不重复子模块源码细节。
- 不把单个类作为父模块架构图的主节点。

## Java/Spring 重点

优先关注：

- `RestController` / `Controller` 对外入口。
- `Service` 到 `Repository` 的调用链。
- 领域聚合、实体、仓储接口和仓储实现。
- Feign 或其他远程客户端。
- MQ producer/consumer 和 job handler。
- `Configuration`、`Bean`、properties 类对运行时行为的影响。
- 模块依赖方向是否符合预期分层。
