# Java 验证与登录策略

以下默认值来自 Bukkit/Folia 和代理配置模板. 使用代理时, Java 共享登录策略在**代理**配置. 部分选项仅用于 Bukkit/Folia 后端或独立服, 请按各行的生效范围设置. 不要在 Fabric 后端配置文件中添加这些选项.

| 选项 | 默认值 | 含义与边界 |
| --- | --- | --- |
| `autoRegister` | `true` | 选择符合条件的新正版名字进入验证/注册路由. 改名与白名单路径独立, 不是所有注册的否决开关 |
| `autoLogin` | `true` | 账号归属允许时, 为已验证 Java 身份提供免密动作. 关闭后不自动登录/绑定/注册 |
| `auto-register-unknown` | `true` | 为已验证 Java 身份创建缺失本地账号. 关闭则由认证插件注册, 不为未知离线名字自动建号 |
| `premiumUuid` | `true` | 使用已验证 Mojang UUID; false 保留按名字生成的离线连接 UUID. 不关闭身份验证 |
| `nameChangeCheck` | `true` | 查询新名字并按 UUID 协调已有 FLP 记录, 不迁移世界数据 |
| `secondAttemptCracked` | `false` | 验证失败后, 允许符合条件的同名玩家下次使用密码登录; 详见[名单与准入](../accounts/lists.md) |
| `offline-whitelist` | `false` | 根据 FLP 已知名单拒绝未知离线 Java 名字, 独立于密码/建号 |
| `forwardSkin` | `true` | 转发包含披风的正版纹理属性, 受支持的 SkinsRestorer 自定义皮肤优先 |
| `premium-warning` | `true` | 玩家自己申请正版绑定时需要再次输入命令确认; 管理员指定其他玩家时不需要 |
| `kick-toggle` | `true` | 适用的账号状态变化后踢出玩家; 玩家需要手动重连. `false` 保留当前连接 |
| `respectIpLimit` | `false` | 后端 AuthMe 注册 IP 限制检查, 代理后面也生效, 不是所有钩子的通用能力 |
| `verifyClientKeys` | `false` | 独立服 ProtocolLib 对适用客户端的公钥签名验证, 不是 Mojang 会话验证, 也不是代理选项 |
| `useProxyAgnosticResolver` | `true` | 独立服会话验证省略 IP 参数, 用于 DNAT 等环境. 代理模板不包含此项, 代理原生验证另行处理 |
| `require-declared-proxy-policy` | `false` | Bukkit/Folia 后端要求代理提供登录和注册策略. 升级代理并确认支持后再启用 |
| `verify-backend-messages` | `true` | 代理检查后端发送的账号管理消息. 仅在受控的旧版本升级期间临时关闭 |

## 按目标选择配置

- 要验证 Java 正版但保留旧离线玩家数据, 检查 `premiumUuid: false`, 不要关闭验证.
- 要保留密码/注册流程而不使用 FLP 免密动作, 使用 `autoLogin: false`, 先评估已转换且无密码的账号.
- 要让没有本地账号的已验证玩家自己设置密码, 使用 `auto-register-unknown: false`, 不代表允许未验证连接进入已绑定账号.
- 要限制新离线名字准入, 使用 `offline-whitelist: true` 并管理 FLP 名单, 只有认证数据库记录不够.

代理策略优先于后端同名配置. 较旧版本混用时可能使用后端本地或默认策略; 建议升级两端并检查登录后, 再启用严格的代理策略要求.

另见[登录方式](../accounts/login.md), [名单语义](../accounts/lists.md), [UUID 风险](../accounts/uuid.md).
