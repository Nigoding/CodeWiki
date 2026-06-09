# VueWiki Analyzer

VueWiki Analyzer 是一个面向 Vue2 前端仓库的静态分析工具。它不生成 Markdown 文档，只输出给 nanobot、nanoclaw 或 CodeWiki skill 使用的 JSON artifacts。

它的目标是从前端代码中提取：

```text
业务模块 -> 页面 -> app.vue 入口 -> 页面子组件 -> 触发点 -> API 调用顺序 -> 后端接口 method/path
```

这些结果可以和 `javawiki-analyzer` 的后端 `entry_points` 对齐，生成开发人员快速理解业务流程的文档。

## 支持范围

MVP 已支持：

- 本地仓库路径和远端 Git URL。
- 远端仓库 clone，默认临时 clone，传 `--keep-clone` 可保留源码。
- Git submodule 初始化和状态记录。
- 从 `owl.config.js` 的 `bundlerConfig.pages` 读取业务模块和页面。
- 页面入口优先定位 `src/<module>/<page>/app.vue`。
- 递归分析 `app.vue` 挂载的子组件。
- 提取 `.vue` 中的生命周期、methods、watch 和 template 事件。
- 提取常见 `request({ url, method })`、`axios.post(url)`、`axios.get(url)` API 定义。
- 建立页面触发点到 API 调用的静态流程。

暂不支持：

- 完整 Vuex 状态流。
- 动态组件完整解析。
- mixin 注入方法分析。
- 动态拼接 URL 的精确求值。
- 运行时条件分支真实执行路径。
- TypeScript 深度类型分析。

## 使用方式

本地仓库：

```bash
vuewiki analyze D:\project\front\demo -o .frontend-analysis
```

远端仓库：

```bash
vuewiki analyze https://github.com/org/front.git -o .frontend-analysis
```

不安装 CLI 时，可以直接运行：

```bash
node vuewiki-analyzer/src/cli.js analyze vuewiki-analyzer/examples/owl-vue2 -o vuewiki-analyzer/.tmp-analysis
```

## 输出文件

```text
.frontend-analysis/
├── analysis.json
├── module_tree.json
├── page_index.json
├── component_index.json
├── api_index.json
├── page_flows.json
├── backend_api_usage.json
├── modules/
├── pages/
└── components/
```

关键文件：

- `analysis.json`：仓库信息、框架信息、统计和诊断。
- `module_tree.json`：来自 `owl.config.js` 的业务模块。
- `page_index.json`：页面入口、中文标题、bizId、组件和流程。
- `component_index.json`：入口组件和子组件关系。
- `api_index.json`：前端 API 函数到后端 method/path 的映射。
- `page_flows.json`：页面生命周期、事件和子组件触发的 API 调用顺序。
- `backend_api_usage.json`：用于和后端接口分析结果对齐的 method/path 清单。

## owl.config.js 约定

MVP 优先读取：

```js
module.exports = {
  bundlerConfig: {
    pages: {
      pension: {
        kycprocess: { title: 'KYC流程', bizId: '4737048' }
      }
    }
  }
}
```

解释：

- `pension` 是业务模块。
- `kycprocess` 是页面。
- `title` 是业务文档中的页面名称。
- `bizId` 是业务标识。
- 页面入口优先找 `src/pension/kycprocess/app.vue`。

## 可信度

分析结果会带 `confidence`：

- `high`：字面量 method/path，生命周期或同一方法内直接调用。
- `medium`：template 事件、子组件事件、watch 触发。
- `low`：动态 URL 或无法确认的调用关系。

MVP 不尝试模拟真实运行时，只提取静态可证明的页面核心调用流程。
