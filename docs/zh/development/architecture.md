# 架构与模块边界

| 模块 | 运行下限 | 职责 |
| --- | --- | --- |
| `protocol` | 8 | 无平台依赖的身份/账号消息契约 |
| `shared` | 8 | 无依赖配置 I/O, 语言/回退和小工具 |
| `craftapi` | 8 | 内置 Mojang 客户端, UUID 编码/查询/会话操作 |
| `core` | 8 | 经典共享路由, 记录, 认证接口, 反机器人与转发模型 |
| `bukkit` | 8 | Spigot/Paper 数据包/前置登录集成与后端认证执行 |
| `folia` | 21 | 按区域调度适配的手工 Bukkit 镜像 |
| `bungee` | 17 | Bungee 身份, 名单持久化, 转发及可选 BungeeAuth 钩子 |
| `velocity` | 17 | Velocity 身份, 名单持久化与转发声明 |
| `fabric26_2` (`fabric/mc26_2`) | 25 | FabricProxy-Lite 转发身份与 EasyAuth 账号决策 |

这是插件字节码下限, 宿主软件可以要求更高 Java. 默认构建包含 Fabric, Gradle 运行在 JDK 25.

## 代理和后端

代理决定验证路由, 建立 Mojang/Floodgate 身份, 实施网络准入, 持有 FLP 记录并执行代理侧写入/转发. 后端读取本地认证账号, 在代理声明策略约束下做最终认证动作决策, 再注册/登录, 保持密码锁或拒绝.

任何一端不能替代另一端的权威. 后端消费有效声明后, 不应凭名字, UUID 外形或独立 Floodgate 检测再造一套身份结论. 本地认证动作未确认前, 代理不能宣称账号访问成功.

## 源码边界

`core` 不导入平台 API, `craftapi` 不负责 FLP 业务规则. Fabric 使用 `protocol` 和 `shared` 的源码, 不依赖 Bukkit/Folia 或代理插件的 JAR.

Bukkit 和 Folia 是独立源码, 包含不同的调度和平台适配. 修改共同行为时应检查两边, 不能直接用 Bukkit 源码覆盖 Folia.

## 持久化边界

代理 FLP 记录是网络名单的来源, 后端 AuthMe/EasyAuth 保存本地账号. 两处更新不是同一个数据库事务, 不能假设一处成功就代表另一处也成功.

另见[登录流程](../LOGIN-FLOW.md)和[事件与扩展](extensions.md).
