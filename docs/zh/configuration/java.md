# Java 验证与登录策略

以下默认值来自当前经典平台模板. 代理网络中的路由和声明策略在**代理**设置, 不在后端副本设置. Fabric 接收这些事实, 不在本地 YAML 定义这些选项.

| 选项 | 默认值 | 含义与边界 |
| --- | --- | --- |
| `autoRegister` | `true` | 选择符合条件的新正版名字进入验证/注册路由. 改名与白名单路径独立, 不是所有注册的否决开关 |
| `autoLogin` | `true` | 账号归属允许时, 为已验证 Java 身份提供免密动作. 关闭后不自动登录/绑定/注册 |
| `auto-register-unknown` | `true` | 为已验证 Java 身份创建缺失本地账号. 关闭则由认证插件注册, 不为未知离线名字自动建号 |
| `premiumUuid` | `true` | 使用已验证 Mojang UUID; false 保留按名字生成的离线连接 UUID. 不关闭身份验证 |
| `nameChangeCheck` | `true` | 查询新名字并按 UUID 协调已有 FLP 记录, 不迁移世界数据 |
| `secondAttemptCracked` | `false` | 为未同意名字在结算失败后启用持久化同名离线回退 |
| `offline-whitelist` | `false` | 根据 FLP 已知名单拒绝未知离线 Java 名字, 独立于密码/建号 |
| `forwardSkin` | `true` | 转发包含披风的正版纹理属性, 受支持的 SkinsRestorer 自定义皮肤优先 |
| `premium-warning` | `true` | 自助 premium 请求需要重复命令确认. 代理切换请求包含当前 Fabric 自助路径, 使用代理确认; 管理员指定他人不走该确认 |
| `kick-toggle` | `true` | 适用的状态变化后踢出, 由玩家手动重连应用新路径. False 保留会话, 不 transfer 或自动重连 |
| `respectIpLimit` | `false` | 后端 AuthMe 注册 IP 限制检查, 代理后面也生效, 不是所有钩子的通用能力 |
| `verifyClientKeys` | `false` | 独立服 ProtocolLib 对适用客户端的公钥签名验证, 不是 Mojang 会话验证, 也不是代理选项 |
| `useProxyAgnosticResolver` | `true` | 独立服会话验证省略 IP 参数, 用于 DNAT 等环境. 代理模板不包含此项, 代理原生验证另行处理 |
| `require-declared-proxy-policy` | `false` | 经典后端兼容开关, 强制登录/注册消息必须带策略事实. 代理升级后再启用, 不替代认证/白名单 |
| `verify-backend-messages` | `true` | 仅代理, 根据回传可信代理集合检查后端 toggle/delete/success 消息. 仅在受控旧端迁移时临时关闭 |

## 按目标选择配置

- 要验证 Java 正版但保留旧离线玩家数据, 检查 `premiumUuid: false`, 不要关闭验证.
- 要保留密码/注册流程而不使用 FLP 免密动作, 使用 `autoLogin: false`, 先评估已转换且无密码的账号.
- 要让没有本地账号的已验证玩家自己设置密码, 使用 `auto-register-unknown: false`, 不代表允许未验证连接进入已绑定账号.
- 要限制新离线名字准入, 使用 `offline-whitelist: true` 并管理 FLP 名单, 只有认证数据库记录不够.

代理声明覆盖后端副本. 经典旧端兼容路径在没有声明块时可能使用本地/默认策略, 这是过渡行为, 不是推荐的 1.0.0 部署. 身份证明缺失或无效时, 不能用名字/UUID 猜测补齐. 两端升级并测试后再收紧兼容开关.

另见[登录方式](../accounts/login.md), [名单语义](../accounts/lists.md), [UUID 风险](../accounts/uuid.md).
