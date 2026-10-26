# CraftAPI 内置基线

[English reference](https://github.com/Hayston1001/FastLoginPlus/blob/main/docs/en/CRAFTAPI-BASELINE.md)

`craftapi/` 是维护中的上游 [CraftAPI](https://github.com/games647/CraftAPI) 内置副本, 提供 Mojang 名字到 UUID 查询, 独立服 `hasJoined` 会话验证, 离线 UUID 算法和 UUID 存储格式. FLP 负责路由, 重试策略和账号决策. 代理原生会话验证及 FLP 可达性诊断与这个客户端的身份操作不同.

Gradle 模块为 `:craftapi`, 不单独发布. 保留 `com.github.games647.craftapi` 包名, 内置到四个经典平台 JAR. Fabric 不打入此客户端, 使用 Velocity 已验证身份.

## 为什么内置

- 上游 `0.8.1` 标签指向 `863ecfac` (2024-05-13, Java 11/HttpClient), 之前实际分发的 Java 8/HttpURLConnection 产物来自 `6f0ded9f` (2024-05-05), 没有对应标签.
- 上游 `1.0` 使用 Java 11 字节码, 并移除出口地址 API. 直接升级会改变 Bukkit Java 下限及 `ip-addresses` 能力.
- 本地需要修复请求限流, 代理回退和名字合法性, 并用离线测试保持稳定契约.

本文保留模块来源和有意差异, 不把旧测试结果当成本次验收. 文档不打进服务器 JAR.

## 上游基线

| 项目 | 记录 |
| --- | --- |
| 仓库 | `https://github.com/games647/CraftAPI` |
| 导入提交 | `6f0ded9f`, 2024-05-05 |
| 目标 | Java 8, class-file 52 |
| 导入规模 | 28 个主源码, 10 个测试源码, 一个公钥资源 |
| 许可证 | [Unlicense](https://github.com/Hayston1001/FastLoginPlus/blob/main/craftapi/src/main/resources/META-INF/LICENSE-CraftAPI-Unlicense.txt), [FastUUID MIT](https://github.com/Hayston1001/FastLoginPlus/blob/main/craftapi/src/main/resources/META-INF/LICENSE-FastUUID-MIT.txt) |

## 本地差异

1. 主端点 403 后切到备用 Mojang 端点, 两端都拒绝时抛错, 不当作 cracked.
2. 429 代理重试保留完整名字 URL, 仍被限流时抛 `RateLimitException`.
3. `setMaxNameRequests` 限制到 `0..600` 并重建窗口限流器; 0 表示不直连, 需要代理.
4. 代理连接保留完整 URL, 空代理选择按 DIRECT 处理, 不抛索引错误.
5. 名字查询 200 必须包含 id/name, 204/404 才代表不存在, 其他状态是错误. `hasJoined(..., null)` 省略 IP 参数, 畸形 200 不抛 NPE.
6. 连接使用 HttpURLConnection, SSL 工厂只用于 HTTPS, 畸形 JSON 转为 IOException.
7. 每次获取时清理全部过期桶, 不只弹出一个.
8. 静态初始化预热默认 SSL 工厂, 避免首次请求承担初始化开销.
9. 端点字段对测试包可见, FLP 不把端点改写暴露成用户配置.
10. 403 备用状态与单次请求使用的端点分离, 修复并发首次回退竞争.
11. 非 200 响应消费错误体, 未建立响应的连接释放, 保持连接复用和故障资源边界.
12. 名字模式为 `^[a-zA-Z0-9_]{2,16}$`, 修正上游 `[A-z]` 意外接受的字符范围, 同时保留下划线.

另有 MIT 短许可头, Checkstyle 整理, `FastUUID` final 和 `Textures.KEY` static final 等有意源码差异. 不是所有本地修改都来自同一个上游提交.

## 不可静默改变的契约

- `UUIDAdapter.generateOfflineId`, `toMojangId`, `parseId` 决定离线身份与数据库 UUID 格式, 必须保留 `UUIDAdapterGoldenTest`.
- 编译依赖由版本目录管理, 代理有意使用自带 Gson/Guava. Bukkit/Folia 打包版本按 `gradle/platform-common.gradle` 的服务端基线固定, 不能用单纯统一版本破坏运行地板.
- 当前源码列表或依赖数量变化时重新核对, 不把导入时的文件/类数量当作永恒断言.

## 更新与验证

导入上游变化前重新检查标签/产物差异, Java 地板, 出口地址 API 和上述差异. 运行 `./gradlew :craftapi:test`, 包含上游派生测试, 本地 HttpServer/raw-socket 和黄金向量, 不依赖真实 Mojang 请求. Windows 使用 `gradlew.bat`.

导入时记录的类一致性为 32 个类, 四个经典产物各一份及两份许可. 它不是本次构建检查结果. 应重新运行字节码/产物检查, 并用 `:core:dependencies --configuration compileClasspath` 确认依赖本地工程. 另见[模块边界](development/architecture.md).
