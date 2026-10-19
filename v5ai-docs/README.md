# v5ai-nb 文档站

独立的 VitePress 中文文档站，文档源文件位于 `zh/`。产品 API、架构与部署内容来自仓库源码、`README.md`、`AGENTS.md` 和 `docs/`；更新实现时应同步校对对应页面。

```bash
cd v5ai-docs
npm install
npm run dev
npm run build
```

`npm run build` 的静态产物位于 `dist/`，可部署到 `https://ai-docs.v5ai.xin` 的站点根路径。图片资源放在 `public/images/`。VitePress 的本地搜索不需要外部搜索服务。
