# AgentOps Hub Frontend

## 使用方式

前端是静态页面，可使用任意 static server 启动，例如：

```bash
cd frontend
python3 -m http.server 5173
```

## 页面入口

- Task Creation: `http://<host>:5173/index.html`
- Task Detail: `http://<host>:5173/task.html?taskId=<id>`
- Trace Replay: `http://<host>:5173/replay.html?taskId=<id>`
- Docs Management: `http://<host>:5173/docs.html`
- RAG Debug: `http://<host>:5173/rag.html`
- Tool Catalog: `http://<host>:5173/tools.html`
- Permission Config: `http://<host>:5173/permissions.html`
- Security Audit: `http://<host>:5173/audit.html`

## API 地址配置

编辑 `config.js`：

```js
window.__APP_CONFIG = {
  API_BASE_URL: "http://<backend-server-ip>:8080"
};
```

这样可以在“前端测试机”访问“后端服务器”。
