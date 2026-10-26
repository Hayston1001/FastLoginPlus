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

## 职责不是"代理决策, 后端执行"

代理决定验证路由, 建立 Mojang/Floodgate 身份, 实施网络准入, 持有 FLP 记录并执行代理侧写入/转发. 后端读取本地认证账号, 在代理声明策略约束下做最终认证动作决策, 再注册/登录, 保持密码锁或拒绝.

任何一端不能替代另一端的权威. 后端消费有效声明后, 不应凭名字, UUID 外形或独立 Floodgate 检测再造一套身份结论. 本地认证动作未确认前, 代理不能宣称账号访问成功.

## 源码边界

`core` 不导入平台 API. `craftapi` 不负责 FLP 业务规则. `shared` 无生产依赖或内置资源, 调用者提供解析器, 资源与策略. Fabric 使用 `protocol`/`shared` 在自身命名空间生成的源码副本, 不依赖经典平台类或 JAR, 产物不得含旧 `com.github.games647.fastlogin` 引用.

Bukkit/Folia 保留兼容包 `com.github.games647.fastlogin.bukkit`, 但为独立源码, 存在刻意保留的调度/平台差异. 不要批量覆盖 Folia. Bungee 内部实现通过已有反射边界访问, 不静态链接未发布内部类.

## 持久化边界

代理 FLP 记录是网络名单权威, 后端 AuthMe/EasyAuth 是本地账号权威. 协议/档案事实, 待转发请求和账号更新生命周期不同. 按名字加锁避免部分经典读改写竞争, 但不是跨库事务或多代理全局锁.

当前默认构建没有 `web` 模块. WebUI 功能分支只有明确移植, 构建和验收后才能作为正式 1.0.0 能力. 另见[登录流程](../LOGIN-FLOW.md), [扩展边界](extensions.md), [shared 模块说明](https://github.com/Hayston1001/FastLoginPlus/blob/main/shared/README.md).
