# 功能与边界

FLP 是 [FastLogin](https://github.com/TuxCoding/FastLogin) 的活跃维护分支. 它负责衔接身份验证与认证插件, 不是认证插件的替代品, 也不会给未经验证的客户端赋予正版身份.

## 它能做什么

- 选择需要 Mojang 验证的 Java 连接, 通过服务器或代理的登录握手确认所有权.
- 在策略和账号归属允许时协调自动注册与免密登录. 已有密码账号不会因为名字与正版账号相同, 就失去原有所有权.
- 管理 premium/cracked 记录, 可限制未知离线用户名进入.
- 选择正版或离线连接 UUID, 转发皮肤, 尊重受支持的 SkinsRestorer 自定义皮肤.
- 支持 Bukkit/Folia 独立服以及 BungeeCord/Velocity 网络. Fabric 26.2 使用独立的 Velocity/EasyAuth 后端集成.
- 在各平台允许的范围内对接 Floodgate 身份.
- 提供连接限制, 中英文消息, 更新检查和后端管理命令.

## 它不负责什么

- 不提供 Minecraft 账号, 不凭名字判断所有权, 不把离线客户端变成通过验证的 Java 客户端.
- 不自动迁移不同 UUID 之间的背包, 世界, 权限或其他插件记录.
- 不替代 Geyser, Floodgate, 认证插件, 转发配置, 后端网络隔离或专业抗 DDoS 服务.
- 不保证所有 Minecraft 版本, 认证插件版本, Fabric 整合包和数据库拓扑的行为相同.
- 切换账号状态不会自动重连玩家. 需要改变登录方式时, 由玩家手动重连.

## 三个独立的问题

1. **准入:** 玩家能否进入? 例如 `offline-whitelist` 拒绝未知离线用户名.
2. **身份:** 当前连接是否证明了 Java 或 Floodgate 身份?
3. **账号访问:** 这个身份是否能免密打开本地认证账号?

通过其中一项不代表其他项也通过. 先看[运行要求](requirements.md), 再看[登录方式](../accounts/login.md).
