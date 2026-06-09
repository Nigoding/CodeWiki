# 远程调用识别规则

文档生成时**必须**先识别"调用外部中台 / 第三方服务"的代码，因为这通常是业务流程的关键节点。优先使用 analyzer 的 `remote_endpoints`；只有当字段缺失或不完整时才执行 fallback 识别。

## 1. 优先来源（analyzer 已提供）

读取 `components/*.json` → `remote_endpoints` 与 `modules/*.json` → `remote_endpoints` / `external_systems`。每项含：

- `kind`：`rest_template` / `web_client` / `okhttp_client` / `http_client` / `rest_client` / `remote_client_feign` / `remote_client_http_exchange`
- `http_method`：`GET` / `POST` / `PUT` / `DELETE` / `PATCH` / `ANY`
- `url`：字符串字面量、`${...}` 占位符，或 `null`（未解析）
- `target_method`：调用入口方法名（如 `getForObject`）
- `client_field`：客户端字段名（如 `restTemplate`）
- `via_method`：哪个本地方法发起的调用
- `line`：源码行号

文档中引用远程调用时，使用这些字段直接填充 [流程章节模板](flow-section-template.md)。

## 2. Fallback 识别（analyzer 未捕获或 schema < 1.1）

以下情形必须执行 fallback：

- `modules/*.json` 的 `remote_endpoints` 为空，但模块中至少一个组件的 `imports` 中含远程客户端类型。
- 组件的 `source_code` 中出现 `restTemplate.`、`webClient.`、`httpClient.` 等链式调用但 `remote_endpoints` 未捕获。
- `schema_version < 1.1`。

Fallback 步骤（顺序检查）：

### 2.1 imports 关键字

扫描组件 `imports`，命中以下任一即视为该组件有远程出站能力：

- `org.springframework.web.client.RestTemplate` / `AsyncRestTemplate` / `RestClient`
- `org.springframework.web.reactive.function.client.WebClient`
- `okhttp3.OkHttpClient`
- `java.net.http.HttpClient`
- `org.springframework.cloud.openfeign.FeignClient`
- `org.springframework.web.service.annotation.HttpExchange` / `GetExchange` / `PostExchange` / `PutExchange` / `DeleteExchange` / `PatchExchange`
- `feign.RequestLine` / `feign.Headers`

### 2.2 类注解判定

- `@FeignClient(name="<svc>", url="<url>")` → 该接口的每个方法都是出站端点；`url` 参数即目标。
- `@HttpExchange(url="...")` 或 Spring 6 HTTP interface → 同上。

### 2.3 字段类型判定

`fields[]` 中类型命中 `RestTemplate` / `WebClient` / `OkHttpClient` / `HttpClient` / `RestClient` → 该字段是远程客户端。记录字段名（如 `restTemplate`）供下一步定位调用。

### 2.4 方法体调用扫描

读 `source_code`，匹配 `<remoteClientField>.<method>(...)` 的链式调用：

- `RestTemplate`：`getForObject` / `getForEntity` / `postForObject` / `postForEntity` / `postForLocation` / `put` / `delete` / `patchForObject` / `exchange` / `execute`
- `WebClient` / `RestClient`：`get()` / `post()` / `put()` / `delete()` / `patch()` / `method()`
- `HttpClient`：`send` / `sendAsync`

### 2.5 URL 提取

按以下顺序在调用参数中提取第一个 URL 候选：

1. 字符串字面量：`"http://..."` 或 `"/api/..."` → 直接使用。
2. 占位符：`${some.config.url}` → 使用 `${some.config.url}` 原样写入，并在文档中标注"需结合 `application.yml`/`bootstrap.yml` 解析实际地址"。
3. 字符串拼接 / `String.format(...)` / `UriBuilder` → 取出可见字面量部分，剩余部分用 `<runtime>` 表示。
4. 完全无法识别 → 写 `<unresolved>`，并在文档"维护注意事项"中标记。

### 2.6 HTTP 方法推断

- 由调用方法名映射（如 `getForObject` → `GET`，`postForEntity` → `POST`）。
- WebClient/RestClient 链式：`get().uri(...)` → `GET`；`method(HttpMethod.X, ...)` → `X`。
- 完全推断不出时写 `ANY`。

## 3. 配置文件交叉引用

发现 `${...}` 占位符时，建议在文档中追加一行"配置项：见 `application.yml` 或 `bootstrap.yml`"。不强制读取 yml；如需读取，仅在用户授权且模块文档 Configuration 章节用得到时进行，并明确引用 `<file>:<line>`。

## 4. 输出要求

无论使用 analyzer 数据还是 fallback：

- **Integration Points 表**必须列出该模块所有远程端点，至少包含列：HTTP 方法、URL（或占位符）、调用类、来源（`analyzer` / `fallback`）。
- **流程章节**：当一个入口的调用链命中任何远程端点时，将其升级为"重要入口"，套 [flow-section-template.md](flow-section-template.md)。
- **维护注意事项**：
  - 若使用 fallback，标注"远程端点经源码 fallback 识别，未由 analyzer 提供，下次升级 analyzer 后建议重新生成文档"。
  - 列出 `<unresolved>` URL 数量，提示后续补全字面量解析或重跑 analyzer。

## 5. 反模式

不允许出现以下表述（缺信息且不可追溯）：

- "通过 RestTemplate 调用外部服务"（无方法、无 URL、无客户端字段）。
- "调用第三方接口获取数据"。
- "Feign Client 与中台通信"（无 URL、无接口方法）。

替换为：

- "`OrderService` 第 102 行通过 `stockFeignClient.freezeStock()` 调用 `${inventory.platform.url}/inventory/freeze`（POST）"。
