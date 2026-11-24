# Geyser 与 Floodgate

Geyser 让基岩版玩家连接 Java 服务器. Floodgate 让基岩版玩家无需 Java 版账号即可进入支持的服务器. FLP 不能代替它们: 请先按 [Geyser](https://geysermc.org/wiki/geyser/setup) 和 [Floodgate](https://geysermc.org/wiki/floodgate/setup) 的指南安装和配置.

## 玩家如何登录

| 使用方式 | 玩家登录方式 |
| --- | --- |
| Geyser + Floodgate | 可按 FLP 的 Bedrock 策略自动注册或登录. 实际支持范围取决于后端认证插件 |
| Geyser, 不使用 Floodgate | 登录方式取决于 Geyser 的认证设置. 使用 Java 正版账号登录时, FLP 可以按 Java 玩家处理 |

仅有相同用户名不能证明玩家拥有 Java 正版账号. 不要把未经过验证的玩家当作正版玩家免密登录.

代理网络中, 在代理配置中设置 Bedrock 策略, 见 [Velocity](../configuration/velocity.md) 或 [BungeeCord](../configuration/bungeecord.md). 后端仍需要兼容的认证插件. 如果使用 AuthMe, 请先查看 [AuthMe 版本区别](authme.md). 修改 Floodgate 玩家名前缀前, 请先备份账号数据, 因为名称变化可能影响已有账号.

## 启用 Bedrock 登录前

- **AuthMe 的用户名字符**: AuthMe 只接受符合 `plugins/AuthMe/config.yml` 中 `settings.restrictions.allowedNicknameCharacters` 的名字 (默认 `[a-zA-Z0-9_]*`). Floodgate 默认前缀是点号, 所以 `.Steve` 这样的名字会被 AuthMe 踢出; 需要把点加入规则, 例如 `[a-zA-Z0-9_.]*`. 只放宽实际需要的字符, 或把 Floodgate 配置为不使用前缀.
- **版本对应**: Geyser 决定对外宣告的 Java 协议版本, 代理与后端必须支持同一个 Minecraft 版本; 例如宣告 26.3 的 Geyser 需要对应的 Velocity 和 Paper. FLP 跟随所在版本, 不能补充协议支持.
- **名字冲突**: 冲突检查比较的是转发后的名字. 前缀非空且本身不是合法 Java 名时, 不会发生冲突; 要验证 `allowFloodgateNameConflict: false`, 应使用空前缀 (或能生成合法 Java 名的前缀), 并接受随之而来的账号名变化.

## Fabric 的支持范围

Velocity + Fabric/EasyAuth 目前只支持符合条件的**未关联 Java 账号**的 Floodgate 玩家自动登录已有 EasyAuth 账号. 账号需要已完成注册, 名称和 UUID 与玩家一致; 代理也必须允许自动登录.

此组合目前不支持自动创建 Bedrock 账号, 已关联 Java 的 Floodgate 账号登录, 或将 Bedrock 账号转换为 Java 正版账号. 登录失败时, 不要关闭身份检查或伪造 UUID; 请先检查账号和代理设置. 安装要求见 [Fabric 指南](../installation/fabric.md).
