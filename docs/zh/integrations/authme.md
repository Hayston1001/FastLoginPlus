# AuthMeReloaded

AuthMe 运行在 Bukkit/Paper/Folia 后端, 不能把后端 JAR 装到 Velocity/BungeeCord. 可选的代理伴生插件是不同插件. 选择兼容服务端的版本, 分别检查 AuthMe 与 FLP 是否成功启动.

## 5.x 与 6.x 不同

FLP 提供 AuthMe 密码/注册动作钩子. AuthMe 6 还具有正版账号状态, 缓存和适用 Paper/Folia 上的 pre-join 对话框. FLP 检测这些能力并协调正版处理.

6.x 接管期间, FLP 启用 `settings.enablePremium` 并禁用 AuthMe 自带正版验证监听器, 避免两套系统各自验证同一连接. AuthMe `/premium` 和 `/freemium` 被拦截并指向 `/flp premium` 与 `/flp cracked`. FLP 会在适用的 AuthMe reload/enable 事件后重新维持接管, 不代表通用服务端热重载已受支持.

管理 AuthMe 代理伴生插件时, FLP 将其 `premium.keepOfflineUuidCompatibility` 固定为 `false`, 让 FLP `premiumUuid` 决定连接 UUID. FLP 负责登录决策期间, 也会协调 AuthMe 自身的代理自动登录策略. 不要在伴生插件另设互相矛盾的自动登录路径.

## 首次登录与对话框

普通进入游戏后的插件消息到达之前, 对话框就可能阻塞. 当前集成可以提前接收独立服已验证身份或可信转发事实, 检查账号归属, 在 AuthMe 入口前准备允许的账号动作. Paper configure 与 Spigot pre-login 路径不同.

单独设置 `enablePremium: true` 不能认证客户端. 档案 UUID, 皮肤和名字本身都不够. Legacy 转发需要已验证的签名声明, modern forwarding 必须正确认证. 已有密码账号需要符合其状态的同意, 不能盲目强制注册.

新的 Bedrock pre-login 路径要求 AuthMe 提供对应的按名字注册/强制登录 API, 包含 6.0.1 使用的能力. 不要承诺所有 5.x 钩子都具有该行为. 这条路径生成的 Bedrock 注册凭据不会发给玩家或写入日志. 新建后登录失败使用快照校验清理, 无法确认安全清理时日志会要求管理员修复.

## 账号安全与恢复

- 账号不可读不代表不存在. 损坏的正版 UUID 会保留并拒绝登录, 由管理员诊断, 不替换为猜测身份.
- 自动登录关闭时, 已验证持有人可保留认证插件密码/注册流程; 不可用的无密码占位账号可能需要安全释放或管理员恢复. 归属不匹配不代表可以删除记录.
- `/flp cracked` 会执行 AuthMe 专属缓存和账号清理, 可能删除/重置认证记录, 不同于 EasyAuth 保留密码的解绑. 先备份并安排安全重新注册.
- `respectIpLimit` 开启时, 后端会检查 AuthMe 的每 IP 注册限制, 代理后面也适用.

对话框阻塞时检查两端日志并按[排障指南](../operations/troubleshooting.md)处理. PacketEvents 不能替代 FLP 独立服 ProtocolLib 路径, 关于 AuthMe 自身可选监听器的告警要结合上下文判断.
