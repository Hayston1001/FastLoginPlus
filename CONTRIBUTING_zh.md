# 参与 FastLoginPlus 开发

[English](CONTRIBUTING.md)

欢迎参与代码, 文档, 翻译和测试, 也欢迎提供有助于排查的 Bug 报告.
FastLoginPlus 是 [FastLogin](https://github.com/TuxCoding/FastLogin) 的维护分支.
请保留源码署名和兼容包名; 如果修复同样适用于上游, 可以另提最小化的上游 PR.

## 快速开始

1. Fork 仓库, 克隆自己的副本, 并从预期的基线创建分支.
2. 安装 JDK 25, 使用它运行 Gradle wrapper. 经典模块使用 JDK 21 工具链,
   Fabric 使用 JDK 25. 构建要求与插件及服务器的运行 Java 版本要求不同.
3. 完成改动并运行相关测试. 请求审阅前, 通常应在仓库根目录运行完整构建:

   Windows:

   ```powershell
   .\gradlew.bat build --console=plain
   ```

   macOS / Linux:

   ```sh
   ./gradlew build --console=plain
   ```

4. 推送分支并向 **Hayston1001/FastLoginPlus:main** 提交 PR, 不要误投上游.
   未完成的工作也可以提交为 Draft PR.

只检查某个模块时, 可以使用 `./gradlew :velocity:build` 等任务 (Windows 使用
`.\gradlew.bat`). 完整构建会运行全项目的测试和检查, 包括 Fabric. 详情见
[构建指南](docs/zh/development/build.md).

## 按改动选择检查

- 为变化的决策和失败路径补测试, 不只测试正常情况. 认证或存储改动应考虑
  断线, 重试, 读写失败和重载等情况.
- 修改共享代码时检查受影响的平台. 适用的 Bukkit 改动要同步到 Folia, 保留调度差异,
  并验证两侧.
- 修改协议时同时检查 `protocol`, Velocity 和 Fabric.
- 修改存储或 JDBC 兼容性时, 还应运行 `./gradlew :core:sqliteFloorTest
  --console=plain` (Windows 使用 `.\gradlew.bat :core:sqliteFloorTest --console=plain`).
- 修改身份相关逻辑时, 运行 `:craftapi:test` 并保留 UUID 黄金测试.
  编译通过本身不能证明打包产物的运行兼容性.

请如实说明运行了哪些检查, 哪些场景尚未验证. 单元测试, 构建, 服务器启动,
真实客户端登录和故障注入分别提供不同的验证证据.

## 项目约定

- 修改认证前, 请阅读[登录与身份信任模型](docs/zh/LOGIN-FLOW.md).
  名字查询, UUID 外形或插件消息本身都不能证明身份.
- 平台 API 不进入共享 core 逻辑. 独立的 `bungeecord-config` YAML 库是例外,
  这不意味着 core 可以使用 BungeeCord 平台 API. 详见[模块边界](docs/zh/development/architecture.md).
- 保留有意固定的驱动和依赖兼容基线. 遵守 Checkstyle, `.gitattributes` 和现有
  MIT SPDX 短头, 在适用时保留原作者署名. 注释保持简洁.
- 修改公共配置, 命令或行为时, 同步相关默认值, 平台描述符和用户文档.
  保持 `docs/en/` 和 `docs/zh/` 及其导航一致. 修改消息时保留占位符,
  检查两种内置语言和回退行为. 详见[语言](docs/zh/operations/languages.md).
- 不要提交密钥, 测试服务器数据或本地生成产物.

## Pull request 与问题反馈

使用 [PR 模板](.github/pull_request_template.md)说明改动, 关联问题, 检查结果和尚未
验证的内容. 保持改动聚焦, 并检查当前 CI 结果.

反馈 Bug 时请参考[反馈指南](docs/zh/operations/reporting.md), 删除日志和配置中的
密钥及个人信息. 安全漏洞请在可用时通过 GitHub 私密漏洞报告提交, 不要在公开
Issue 中发布利用细节或密钥.

功能请求和改进建议请发到
[Discussions 的 Ideas 分类](https://github.com/Hayston1001/FastLoginPlus/discussions/categories/ideas).

贡献采用项目的 [MIT License](LICENSE).
