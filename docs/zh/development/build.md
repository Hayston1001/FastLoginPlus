# 构建, 测试与贡献

## 构建环境

用 Git 克隆仓库, 在根目录运行已提交的 Gradle wrapper. **Gradle 必须运行在 JDK 25**. 经典模块使用 JDK 21 编译工具链及各模块 `--release`, Fabric 使用 JDK 25. Foojay 可以提供经典工具链, 不能替代启动 Gradle 所需的 JVM. 旧版 Maven 指令不再适用.

```powershell
.\gradlew.bat build --console=plain
.\gradlew.bat :core:sqliteFloorTest --console=plain
.\gradlew.bat release --console=plain
```

Unix 类系统使用 `./gradlew`. 其他入口有 `test`, `assemble`, 以及 `:bukkit:build :folia:build` 等定向构建. `assemble` 不能替代测试/检查. 发版前应完整 clean 构建并检查实际产物, 不盲目复用旧输出.

## 产物与版本

根 `build.gradle` 是版本来源, 资源处理展开平台描述符并生成 Velocity 构建信息. 经典可安装产物是 shaded JAR, 不是 `-plain.jar`. `release` 构建全部模块, 在 `build/release/` 收集 **5 个**可安装 JAR: Bukkit, Folia, BungeeCord, Velocity, Fabric 26.2. 会检查 CI 标签/版本不匹配和产物数量.

手册面向即将发布的 1.0.0, 不代表当前构建文件已经升版. 不要因为编写功能说明而顺便改版本, 打标签, 推送或发布.

## 检查与测试选择

- `build` 运行测试, Checkstyle, 许可证和适用产物检查. 遵守 `checkstyle.xml` 的 Java 120 字符行长, 160 行方法, import 和 Javadoc 标签要求.
- 已有许可头使用 MIT SPDX 短头, 保留上游贡献者署名. 新 Java/XML/Gradle 使用短头, 不在每个文件粘贴完整许可证.
- `checkRuntimeBytecode`, 经典 `verifyPluginJar`, Fabric `verifyFabricJar` 检查打包依赖/身份. 只有源码兼容级别不能证明运行下限.
- `:core:sqliteFloorTest` 使用刻意固定的最低 SQLite 驱动重跑存储测试, 不只测最新编译依赖. 修改存储时保留驱动地板断言.
- 改查询/会话/离线 UUID 语义需要 `:craftapi:test`, 尤其保留 `UUIDAdapterGoldenTest`. 改协议同时检查 protocol, Velocity 和 Fabric 测试.
- `shared` 禁止平台/第三方生产导入, 资源和依赖包名的反射. Fabric 编译 `protocol` 和 `shared` 的重定位副本, 也要检查生成产物.
- Bungee 源码保留公开 API 边界和 `BungeePublicApiOnlyTest`.
- 测试使用 JUnit 6 与 Mockito. 模拟测试通过不代表启动, 真实客户端认证或失败恢复已验收.

## 贡献规则

平台 API 不进入 `core`. 适用 Bukkit 修改按意图镜像到 Folia, 不复制整目录, 保留区域调度与不同权限. 共享依赖版本放 `gradle/libs.versions.toml`, 刻意固定的服务端/代理/SQLite 基线留在对应 `gradle/*.gradle`.

中英文文档一起维护. 根 README 只作入口, 详细行为写入本手册. 公共行为变化同步默认配置/消息/描述符. 更新历史仅在获得授权的变更/发版流程中写入对应中英文 changelog.

除非明确贡献上游, PR 应提交到 [Hayston1001/FastLoginPlus](https://github.com/Hayston1001/FastLoginPlus). 说明行为变化及实际验证的平台/测试, 不提交密钥, 运行服务器数据和本地报告. 见[贡献指南](https://github.com/Hayston1001/FastLoginPlus/blob/main/CONTRIBUTING_zh.md).
