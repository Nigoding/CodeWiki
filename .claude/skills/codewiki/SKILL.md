---
name: codewiki
description: >
  为代码仓库生成分层 wiki 文档。
  当用户希望对代码仓库进行文档化、分析架构、理解模块关系或创建 wiki 时使用。
  支持 Java、Python、C# 等多种语言。
---

# CodeWiki — 分层仓库文档生成

**设计原则：** CodeWiki 是一个*分析引擎*（通过 MCP 暴露）加上一个*生成策略*（由你执行）。MCP 服务器负责 AST 解析、依赖图构建和基于 LLM 的模块聚类。**你**（Claude）负责使用原生的文件编辑和推理能力来实际撰写文档。

---

## 前置条件

1. **CodeWiki MCP 服务器**必须已注册并正在运行。通常在 MCP 客户端中配置如下：
   ```json
   {
     "mcpServers": {
       "codewiki": {
         "command": "python",
         "args": ["-m", "codewiki_mcp.server"]
       }
     }
   }
   ```
2. **LLM 凭证**必须对 MCP 服务器可用（通过环境变量或 MCP 服务器配置），用于聚类步骤：
   ```bash
   export LLM_BASE_URL="https://api.openai.com/v1"
   export LLM_API_KEY="sk-..."
   export MAIN_MODEL="gpt-4o"
   export CLUSTER_MODEL="gpt-4o-mini"
   ```

---

## 阶段 1 — 分析仓库（MCP 调用）

调用 MCP 工具 **`analyze_repository`**，传入目标仓库路径。

**参数：**
- `repo_path`（必填）：仓库的绝对路径。
- `include_patterns`（可选）：例如 `["*.py", "*.java"]`。
- `exclude_patterns`（可选）：例如 `["*test*", "*spec*"]`。
- `skip_clustering`（可选，默认 `false`）：如果只想获取原始组件而不进行 LLM 模块聚类，设为 `true`。

**工具返回内容：**

| 字段 | 用途 |
|------|------|
| `components` | 所有类 / 接口 / 函数的字典，包含 `source_code`、`depends_on`、`file_path` 等字段。 |
| `leaf_node_ids` | 依赖图中作为叶子节点的组件 ID 列表。 |
| `module_tree` | 分层模块分解结果（例如 `{ "Auth": { "components": [...], "children": { ... } } }`）。空 `{}` 表示仓库很小，可直接生成整库文档。 |
| `processing_order` | 自底向上遍历顺序：**先处理叶子模块，再处理父模块**。 |

> **注意：** 将返回的数据保留在上下文中（或随时引用）。与旧的基于脚本的工作流不同，分析产物直接通过 MCP 响应返回，而非写入磁盘。

---

## 阶段 2 — 生成文档（自底向上）

### 2.1 读取处理顺序

从 MCP 响应中提取 `processing_order`。每个条目格式为 `(module_path_list, module_name)`。**严格按照顺序处理** —— 这能保证每个子模块的 `.md` 文件在生成其父模块概述之前已经存在。

### 2.2 加载参考数据

将 MCP 响应中的 `components` 和 `module_tree` 保留在上下文中。你会通过组件 ID 查找对应的源代码。

如果后续需要查询某个特定组件，且其源代码在初次响应中被截断，可调用 MCP 工具 **`get_component`**，传入 `component_id`。

### 2.3 逐个处理模块

判断当前模块是**叶子模块**还是**父模块**：

- 当 `module_tree[...][module_name].children` 为空或缺失时，该模块为**叶子模块**。
- 否则为**父模块**。

#### 叶子模块策略

1. **收集核心组件**：从 `module_tree[...][module_name].components` 中获取。
2. **读取源代码**：通过 `components` 字典按组件 ID 查找（ID 格式为 `<file_path>::<name>`）。
3. 在用户指定的输出目录中**创建 `{module_name}.md`**，内容需包含：
   - **用途** —— 一段话概括该模块的功能。
   - **架构** —— Mermaid 图展示内部类 / 接口及其关系（继承、实现、关键方法调用）。
   - **核心组件** —— 每个组件的职责、公开 API 和值得注意的逻辑。
   - **依赖关系** —— 当依赖位于本模块之外时，链接到其他模块文档。使用相对 Markdown 链接：`[AuthService](AuthService.md)`。
   - **数据流** —— 如果模块编排了某个流程，使用 Mermaid 序列图或流程图展示。

> **约束：** 不要重复属于子模块文档的信息。如果 `UserService` 依赖 `AuthService`，简要描述交互并链接到 `AuthService.md` 即可。

#### 父模块策略

1. **收集子模块上下文** —— 对 `children` 中的每个直接子模块，读取其已生成的 `.md` 文件。
2. **构建上下文对象**（可在脑中或显式构建）：
   ```json
   {
     "子模块名称": {
       "docs": "<子模块 markdown 文件的完整内容>",
       "components": [...]
     }
   }
   ```
3. **创建 `{module_name}.md`**（根模块则为 `overview.md`），内容需包含：
   - **用途** —— 该逻辑分组实现了什么功能。
   - **架构** —— Mermaid 图用**子模块作为方框**（而非单个类），展示子模块之间的交互。
   - **子模块引用** —— 每个子模块的简要概述，附带指向其文档的 Markdown 链接。
   - **横切关注点** —— 跨多个子模块的模式或约定。

> **约束：** 父模块文档保持*概述级别*。不要直接粘贴子模块文档内容，要提炼总结并链接。

### 2.4 根总览（`overview.md`）

当 `module_path` 为空时（`processing_order` 的最后一个条目代表整个仓库）：

- 文件标题为 `overview.md`。
- 提供端到端的系统架构。
- 引用每个顶层模块。
- 包含整个仓库的高层 Mermaid 图。

---

## 输出目录结构

生成的文档写入用户指定的目录（例如 `./docs/`）：

```
docs/
├── overview.md              # 仓库级总览
├── module_A.md              # 顶层或叶子模块
├── module_B.md
├── submodule_x.md           # 嵌套子模块
└── （分析结果保留在 MCP 上下文中，不写入磁盘）
```

如果用户希望将原始分析产物持久化，你可以将 MCP 响应字段（`components`、`module_tree`、`processing_order`）作为 JSON 文件保存在 markdown 文档旁边。

---

## 增量更新

当用户要求"更新文档"或"变更后重新生成"时：

1. 使用相同的 `repo_path` 再次调用 **`analyze_repository`**。
2. 将新的 `module_tree` / `components` 与之前的知识对比（或询问用户变更了哪些文件）。
3. 找出包含变更组件的模块。
4. 删除受影响的 `{module_name}.md` 文件（以及其父模块的 `.md` 文件，因为父模块概述依赖子模块文档）。
5. 仅在 `processing_order` 中重新处理被失效的模块及其祖先模块。

---

## 独立聚类（高级）

如果你已经从之前的 `analyze_repository` 调用中获得了 `components` 和 `leaf_node_ids`，只想用不同参数重新执行聚类（例如更深的深度、不同的模型），可直接调用 MCP 工具 **`cluster_modules`**：

- `leaf_node_ids`：组件 ID 列表
- `components`：完整的 components 字典
- `max_depth`、`max_token_per_module`、`cluster_model`：可选的覆盖参数

---

## 故障排查

| 问题 | 解决方案 |
|------|----------|
| `components` 字典过大 | 仅将当前模块相关的组件 ID 加载到上下文中，不要一次性实例化整个字典。 |
| `module_tree` 为 `{}`（空） | 仓库小到可以放入单个上下文窗口。直接从 components 生成单个 `overview.md`。 |
| 聚类只产生 1 个模块 | LLM 未能找到逻辑分组；回退到整库文档模式（与空树处理相同）。 |
| 生成父模块时缺少子 `.md` | 处理顺序有误。始终严格遵循 `processing_order`。 |
| Mermaid 图无法渲染 | 确保语法合法（节点 ID 中不要有特殊字符；如有需要，用引号包裹标签）。 |

---

## 设计哲学（供维护者参考）

- **分析是确定性的** —— AST 解析、图构建和拓扑排序都是在 MCP 服务器内部运行的纯代码。
- **聚类由 LLM 辅助但结构化** —— 单次提示，使用严格的 `<GROUPED_COMPONENTS>` 标签；没有 Agent 循环。
- **生成是原生 Agent 行为** —— 你（Claude）使用自己的工具撰写文档，由 MCP 响应指导。这消除了原始单体 CodeWiki 架构中存在的"Agent 套 Agent"冗余。
