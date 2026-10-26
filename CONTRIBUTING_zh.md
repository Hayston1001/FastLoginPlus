# 参与 FastLoginPlus 开发

[English](CONTRIBUTING.md)

欢迎代码, 文档, 翻译和测试贡献. 本项目是 [FastLogin](https://github.com/TuxCoding/FastLogin) 的维护分支, 请保留来源署名和兼容包名. 同样适用于上游的修复, 可以另提最小化上游 PR.

## 构建与测试

使用已提交的 Gradle wrapper, **JDK 25** 运行 Gradle. 经典模块使用 JDK 21 工具链, Fabric 使用 25. 插件运行下限与构建要求是不同概念.

```powershell
.\gradlew.bat build --console=plain
.\gradlew.bat :core:sqliteFloorTest --console=plain
```

Unix 类系统使用 `./gradlew`. `release` 构建全部模块并在 `build/release/` 收集五个可安装 JAR. 不部署 `-plain.jar`, 不为测试改动就发布/打标签. 详细[构建检查](docs/zh/development/build.md)和[模块边界](docs/zh/development/architecture.md)见手册.

## 修改前

- 检查当前分支, 工作区和目标源码, 不覆盖无关工作.
- 认证变化先阅读[登录/身份信任模型](docs/zh/LOGIN-FLOW.md). 名字查询, UUID 外形或插件消息本身都不证明身份.
- 平台 API 不进入共享 core 逻辑. 适用 Bukkit 变化同步 Folia, 保留调度差异; 改协议同时检查 Velocity 与 Fabric.
- 保留刻意固定的驱动/依赖基线和 UUID 黄金向量. 源码编译不证明打包后的运行兼容.
- 注释保持简洁, 已有许可头采用 MIT SPDX 短头并保留适用原作者署名. 遵守 Checkstyle 与 `.gitattributes` (LF, `.bat`/`.cmd` 例外).

## 文档与翻译

完整 1.0.0 手册位于 `docs/en/`, `docs/zh/` 保持对应路径. 根 README 只是简短入口, 不再重复配置手册. 同步两种语言, SUMMARY 和公共行为说明. 文档不能引用私有/本机文件, 不暗示未测试的普遍兼容.

经典与 Fabric 平台分别提供默认消息, 含共享和平台专属键. 保留替换标记和语言版本行为, 修改消息时检查内置两种语言及所选语言/英文兜底. 见[语言](docs/zh/operations/languages.md), [Wiki 维护](docs/zh/development/gitbook.md).

公共配置/命令/行为变化还需同步默认值, 描述符和用户文档. 发布说明在授权下写入 `CHANGELOG.md` 与 `CHANGELOG_zh.md` 的对应开发/发布段, 不把新增中英文混进同一文件, 不重写无关历史版本.

## Pull request

1. Fork 后从预期基线建分支, 目标为 **Hayston1001/FastLoginPlus:main**, 不要误投上游.
2. 尽量采用简洁 Conventional Commit 风格摘要并使用 [PR 模板](.github/pull_request_template.md), 需要时带仓库引用相关 issue.
3. 为决策及失败路径补测试, 不只测正常情景. 运行受影响检查, 请求审阅前通常还需完整 `build`.
4. 分开说明实际证据: 单元测试, 编译, 启动, 真实客户端与故障注入. 适用时覆盖断线/重连, 读写失败与关闭/重载.
5. 不提交密钥, 测试服务器数据和本地生成产物. 精确暂存, 不把整个工作区一并加入.
6. 检查当前 CI. 未完成工作可以提交 draft PR. 提交, 推送, 标签和发布是单独的明确操作, 不包含在准备工作里.

## 反馈与许可证

按[反馈指南](docs/zh/operations/reporting.md)提供脱敏版本/配置/日志. 敏感安全问题优先使用可用的私密漏洞报告.

贡献采用项目 [MIT License](LICENSE).
