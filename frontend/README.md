# frontend — 多语言学习系统前端

Vue 3 + Vite + Pinia + Vue Router 构建。完整项目说明见仓库根目录 [README.md](../README.md)。

## 开发

```sh
npm install
npm run dev        # 默认 http://localhost:5173
```

后端地址默认 `http://localhost:8080`，可通过环境变量覆盖：

```sh
# Windows PowerShell
$env:VITE_API_BASE_URL="http://localhost:8080"
```

## 请求层约定

- `src/api/fetchJson.js`：统一请求封装，返回 `{code, message, data}` 信封，自动注入 JWT 与当前学习语言头，401 自动跳转登录。**页面代码一律使用它**，不要写裸 `fetch`（流式 SSE 接口 `/ai/ask/stream` 除外）。
- `src/api/client.js`：axios 实例（`code === 200` 时直接返回 `data`），供 `src/api/*` 模块使用。
- 所有接口地址统一从 `src/config` 的 `API_BASE_URL` 导入，禁止硬编码。

## 生产构建

```sh
npm run build      # 产物在 dist/，第三方库已按 vue/charts/motion 分包
```
