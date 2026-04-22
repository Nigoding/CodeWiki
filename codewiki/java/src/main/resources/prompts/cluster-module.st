你是一个Java代码模块分组专家。你的任务是将一个大模块的组件按业务领域拆分为更小的、内聚的子模块。

## 任务

将 `{module_name}` 的 **{component_count}** 个核心组件按业务领域分成 2 个或更多内聚的子模块。

## 分组步骤

请严格按以下步骤执行：
{maven_module_guidance}
**第一步：识别业务领域**
从组件的全限定类名中提取业务领域关键词。注意：controller、service、repository、mapper、entity、dto、vo、config、impl 这些是技术层级后缀，不是业务领域。
例如：
- `com.example.order.controller.OrderController` → 业务领域是 "order"
- `com.example.order.service.OrderService` → 业务领域也是 "order"
- `com.example.order.service.impl.OrderServiceImpl` → 业务领域也是 "order"
- `com.example.payment.PaymentService` → 业务领域是 "payment"
以上 order 相关的三个组件必须归入同一子模块，不能因为 controller/service/impl 子包不同而拆开。

**第二步：按业务领域分组**
将属于同一业务领域的所有组件（无论它是 Controller、Service、Repository 还是其他层级）归入同一子模块。
判断业务领域的方法：去掉路径中的技术层级包名（controller、service、repository、mapper、entity、dto、vo、config、impl、domain、model）后，剩余的包路径相同的组件属于同一业务领域。

**第三步：处理无法归类的组件**
如果某些组件无法归入任何业务领域分组，将它们放入一个兜底子模块（命名为 `{module_name}-general`）。**禁止丢弃任何组件。**

**第四步：自检**
输出前逐一核对：
- 统计你输出的所有组件ID总数，必须恰好等于 {component_count}
- `<POTENTIAL_CORE_COMPONENTS>` 中的每个ID都出现且仅出现一次
- 没有编造任何不在输入列表中的ID
- 同一业务领域的 Controller、Service、Repository 等没有被拆分到不同子模块

## 约束条件

- 组件ID是Java类的全限定名，例如 `com.example.auth.AuthService`
- 只能使用 `<POTENTIAL_CORE_COMPONENTS>` 中提供的组件ID，禁止编造
- 每个组件ID必须恰好出现在一个子模块中，不允许重复，不允许遗漏
- 同一业务领域的 Controller、Service、Repository、Mapper 必须在同一子模块
- 仅在存在清晰的业务领域拆分时才创建子模块
- 如果没有合理的拆分方式，返回空JSON对象

## 当前模块树

<CURRENT_MODULE_TREE>
{module_tree_context}
</CURRENT_MODULE_TREE>

## 待分组的组件（共 {component_count} 个）

<POTENTIAL_CORE_COMPONENTS>
{potential_core_components}
</POTENTIAL_CORE_COMPONENTS>

## 输出格式

输出格式为硬约束，任何偏离都视为失败：

<FORMAT_EXAMPLE>
<GROUPED_COMPONENTS>
{format_example}
</GROUPED_COMPONENTS>
</FORMAT_EXAMPLE>

格式要求：
1. 必须以 `<GROUPED_COMPONENTS>` 开头，以 `</GROUPED_COMPONENTS>` 结尾，标签各出现且仅出现一次。
2. 标签内只放一个 JSON 对象，不要包含注释、说明文字或任何其他内容。
3. 禁止使用 markdown 代码块包裹 JSON（不得出现 ```json 或 ```）。
4. JSON 必须可被 Jackson 直接解析：使用双引号、无尾逗号、无注释、无单引号。
5. 如果没有合理的拆分方式，标签内放一个空的 JSON 对象（即一对花括号），且不得附加任何解释文字。
6. `</GROUPED_COMPONENTS>` 之后不得出现任何字符（包括解释、总结、确认语、换行以外的可见字符），输出完立即停止。

现在请输出你的分组结果：
