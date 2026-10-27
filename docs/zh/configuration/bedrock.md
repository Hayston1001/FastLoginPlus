# Bedrock 策略

独立服在服务器的 FLP 配置中设置这些选项; 代理网络在**代理**的 FLP 配置中设置.

| 选项 | 默认值 | 用途 |
| --- | --- | --- |
| `autoLoginFloodgate` | `false` | 允许受支持的 Floodgate 免密登录 |
| `autoRegisterFloodgate` | `false` | 允许受支持的 Floodgate 建号, 同时要求自动登录允许 |
| `allowFloodgateNameConflict` | `false` | 按对应路径冲突规则, 允许未 linked 的 Bedrock 名字与 Java 正版名重合 |

自动认证选项使用以下值:

| 值 | 含义 |
| --- | --- |
| `false` | 不授予该自动动作 |
| `true` | 对受支持的 linked/未 linked 身份允许 |
| `linked` | 只允许 Floodgate 已关联 Java 账号的身份 |
| `no-conflict` | 按适用的 Java 名字冲突检查允许 |

`allowFloodgateNameConflict` 应作为布尔冲突开关, 不当作另一个自动登录选择器. 强烈建议保留不与 Java 冲突的 Floodgate 前缀, 不要为了复用 Java 账号随意删除前缀.

## Spigot/Paper/Folia

在受支持的 AuthMe 版本上, FLP 可以在登录对话框出现前为 Floodgate 玩家处理账号. 自动创建账号需要同时允许自动注册和自动登录. 不同认证插件的支持范围不同, 请查看[集成说明](../integrations/bedrock.md).

Java `auto-register-unknown` 不能替代 `autoRegisterFloodgate`. 准入, 建号, linked 身份和名字冲突是不同检查.

## Fabric 限制

Fabric 只接受文档规定的签名且**未 linked 的已有完整账号**路径. `autoLoginFloodgate` 必须允许该路径 (`true` 或适用的 `no-conflict`), `linked` 不会扩大 Fabric 支持范围. `autoRegisterFloodgate: true` 不会启用 Fabric 建号. EasyAuth 原生 Floodgate 自动登录保持关闭, 防止绕过 FLP 检查.

启用前阅读 [Geyser/Floodgate 配置与信任](../integrations/bedrock.md).
