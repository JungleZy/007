# project-006-frontend

海军报务系统前端（Vue 3 + Vite，运行于外部 Electron 外壳内）。

```bash
npm install
npm run dev        # vite --host
npm run build      # 产物 dist/
```

## 构建风格

`bw-frontend/frontend/vite.config.js` 读取 `NIP_INTERFACE_STYLE`，支持 `HJ`、`HJJ`、`LJ`，默认 HJJ。
Vite 将其编译到 `bw-frontend/frontend/src/config/runtime.js` 的界面初始化逻辑；
`public/runtime-config.js` 用于 Web 服务地址配置，不负责界面风格。修改风格需要重新构建前端，不能仅重命名安装包。

单独构建前端时，在本目录的 PowerShell 中执行（Bash 对应 `NIP_INTERFACE_STYLE=HJ npm run build`）：

```powershell
$previousStyle = $env:NIP_INTERFACE_STYLE
try {
    $env:NIP_INTERFACE_STYLE = 'HJ'
    npm run build
    if ($LASTEXITCODE -ne 0) { throw '前端构建失败' }
} finally {
    $env:NIP_INTERFACE_STYLE = $previousStyle
}
```

多个构建共用 `bw-frontend/frontend/dist/`，不要并发构建不同风格。
龙芯完整安装包请从 `bw-frontend/` 运行专用批量入口，详见
[docs/guides/kylin-loongarch64-packaging.md](../../docs/guides/kylin-loongarch64-packaging.md)。
最终安装包统一放在 `bw-frontend/out/releases/`，验证完成后只保留安装包文件。

文档全部在仓库根 `docs/`（本目录已无 `docs/`）：

- 当前全项目评审（唯一入口）：[`../../docs/reviews/2026-09-12-full-project-review.md`](../../docs/reviews/2026-09-12-full-project-review.md)
- 前后端联合评审详细证据（历史，跨栈契约仍以此为准）：[`../../docs/reviews/2026-09-08-joint-frontend-backend-review.md`](../../docs/reviews/2026-09-08-joint-frontend-backend-review.md)
- 历史前端评审与分片：`../../docs/reviews/archive/`
- 文档地图：[`../../docs/README.md`](../../docs/README.md)
- 单仓总览：[`../../README.md`](../../README.md)；Electron 外壳：[`../README.md`](../README.md)
