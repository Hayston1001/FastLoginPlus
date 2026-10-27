# Geyser 与 Floodgate

Geyser 让基岩版玩家连接 Java 服务器. Floodgate 让基岩版玩家无需 Java 版账号即可进入支持的服务器. FLP 不能代替它们: 请先按 [Geyser](https://geysermc.org/wiki/geyser/setup) 和 [Floodgate](https://geysermc.org/wiki/floodgate/setup) 的指南安装和配置.

## 玩家如何登录

| 使用方式 | 玩家登录方式 |
| --- | --- |
| Geyser + Floodgate | 可按 FLP 的 Bedrock 策略自动注册或登录. 实际支持范围取决于后端认证插件 |
| Geyser, 不使用 Floodgate | 登录方式取决于 Geyser 的认证设置. 使用 Java 正版账号登录时, FLP 可以按 Java 玩家处理 |

仅有相同用户名不能证明玩家拥有 Java 正版账号. 不要把未经过验证的玩家当作正版玩家免密登录.

代理网络中, 在代理配置中设置 Bedrock 策略, 见 [Velocity](../configuration/velocity.md) 或 [BungeeCord](../configuration/bungeecord.md). 后端仍需要兼容的认证插件. 如果使用 AuthMe, 请先查看 [AuthMe 版本区别](authme.md). 修改 Floodgate 玩家名前缀前, 请先备份账号数据, 因为名称变化可能影响已有账号.

## Fabric 的支持范围

Velocity + Fabric/EasyAuth 目前只支持符合条件的**未关联 Java 账号**的 Floodgate 玩家自动登录已有 EasyAuth 账号. 账号需要已完成注册, 名称和 UUID 与玩家一致; 代理也必须允许自动登录.

此组合目前不支持自动创建 Bedrock 账号, 已关联 Java 的 Floodgate 账号登录, 或将 Bedrock 账号转换为 Java 正版账号. 登录失败时, 不要关闭身份检查或伪造 UUID; 请先检查账号和代理设置. 安装要求见 [Fabric 指南](../installation/fabric.md).
