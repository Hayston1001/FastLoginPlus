# 运行要求与部署选择

## 选择部署路径

| 部署方式 | 安装 FLP 的位置 | 认证账号的位置 |
| --- | --- | --- |
| Spigot/Paper 独立服 | Bukkit 后端 | 后端认证插件 |
| Folia 独立服 | Folia 后端 | 兼容 Folia 的认证插件 |
| Velocity + 经典后端 | Velocity 和每个参与认证的 Bukkit/Folia 后端 | 后端认证插件 |
| BungeeCord + 经典后端 | BungeeCord 和每个参与认证的 Bukkit/Folia 后端 | 后端认证插件, 或独立支持的 BungeeAuth 钩子 |
| Velocity + Fabric 26.2 | Velocity 和 Fabric 后端 | Fabric 上的 EasyAuth |

新建 Paper/Folia 代理网络时优先采用 Velocity modern forwarding. 原生 Spigot 不支持 modern forwarding, 应使用 [Velocity 指南的 legacy 分支](../installation/velocity.md), 或使用 BungeeCord 并隔离后端. Fabric 必须通过 FabricProxy-Lite 使用 modern forwarding, 不是 BungeeCord.

## Java 要求

| FLP 产物 | 插件字节码下限 | 其他要求 |
| --- | --- | --- |
| Bukkit | Java 8 | 服务端, 认证插件和数据包库可能要求更高版本 |
| Folia | Java 21 | 兼容的 Folia 服务端及支持 Folia 的依赖 |
| BungeeCord | Java 17 | 同时满足代理自身要求 |
| Velocity | Java 17 | 同时满足代理自身要求 |
| Fabric 26.2 | Java 25 | Minecraft 26.2, Fabric Loader >=0.19.3, Fabric API, FabricProxy-Lite, EasyAuth |

整个安装环境以要求最高的 Java 版本为准. 字节码下限不代表新版 Paper 或 ProtocolLib 能运行在 Java 8. 编译 FLP 是另一件事: Gradle 需要运行在 JDK 25.

## 依赖与支持范围

- Bukkit/Folia 独立服需要兼容的数据包库和认证插件. FLP 针对 ProtocolLib 5.3.0 编译, 实际应选择兼容目标服务端的构建, 不能仅凭版本号大于 5.3.0 判断可用. ProtocolSupport 是另一条兼容路径, 不是代理后端的必装依赖.
- 代理后端不使用 FLP 的 ProtocolLib 拦截, 在线握手由代理负责. 其他插件仍可能单独需要 ProtocolLib.
- AuthMe 5.x 和 6.x 的集成行为不同. 其他认证插件存在钩子, 不代表具备 AuthMe 相同的清理或 pre-join 支持.
- Fabric 的参考集成是 EasyAuth 3.4.4. 加载范围为 `>=3.4.0 <3.5.0`, 不代表所有补丁构建都经过运行验证. 受支持的 Fabric 存储组合使用本地 SQLite; 其他 EasyAuth 存储尚未验证.
- Geyser/Floodgate 和各认证插件还有独立的服务端与 Java 要求, 应查阅各自文档.

产物列表不是覆盖所有 Minecraft 版本的实测矩阵. 对外开放之前, 应按[安装检查清单](../installation/verification.md)验证实际组合. Fabric 支持不自动扩展到其他 Minecraft 版本, linked Bedrock 账号或多后端账号迁移.
