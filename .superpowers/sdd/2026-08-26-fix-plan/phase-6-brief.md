## Phase 6：交付链路

- [ ] **Task 6.1**: `.github/workflows` 加 `on.push.tags: ['v*']`；native job 按架构拆分（ARM64 去 x86 `-march`），产物名嵌 `${{ runner.arch }}`。`actionlint` 校验。
- [ ] **Task 6.2**: `Dockerfile.jvm:91`、`Dockerfile.native:24` `EXPOSE 8080` → `EXPOSE 18001`；本地 `docker build -f Dockerfile.jvm .` 通过。
- [ ] **Task 6.3**: BeanUtils 清理：`lsp references` 找 commons-collections 直接 import（含 `Assert.java:4`）→ 改用 hutool/自研替代或在 pom 显式声明 commons-collections，然后删 BeanUtils 死调用与依赖。
- [ ] **Task 6.4**: 文档勘误：common-build 页首 27→35；controller-api scope 52→63。

