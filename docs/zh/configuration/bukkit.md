# Bukkit 和 Folia 配置

文件: Spigot, Paper 和 Folia 的 `plugins/fastloginplus/config.yml`. 三者使用相同模板和默认值. 停服修改并重启. 代理部署中, 表内标记为**独立服**的共享策略由代理控制, 修改后端副本不会改变代理策略.

Folia 的账号命令使用 `fastloginplus.folia.command.*` 权限前缀, 而不是 Bukkit 前缀. 详见[命令与权限](../accounts/commands.md).

## 通用与登录

| 选项 | 默认值 | 作用 |
| --- | --- | --- |
| `language` | `en` | 消息语言, 内置 `en`/`zh`, 也可使用自定义语言文件 |
| `debug` | `false` | 输出排障日志 |
| `check-update` | `true` | 检查新版本, 不自动安装 |
| `update-check-interval` | `24` | 更新检查间隔, 小时 |
| `autoRegister` | `true` | 独立服: 为符合条件的新名字选择正版验证路径, 不是所有注册的总开关 |
| `autoLogin` | `true` | 独立服: 允许符合条件的已验证玩家免密登录 |
| `auto-register-unknown` | `true` | 独立服: 允许为已验证但没有本地账号的 Java 玩家创建账号 |
| `premiumUuid` | `true` | 独立服: 使用正版 UUID; `false` 使用离线 UUID. 修改前备份玩家数据 |
| `nameChangeCheck` | `true` | 独立服: 检查正版改名并更新 FLP 记录, 不迁移玩家数据 |
| `secondAttemptCracked` | `false` | 独立服: 符合条件的验证失败后允许后续使用密码路径 |
| `offline-whitelist` | `false` | 独立服: 拒绝 FLP 名单中未知的离线 Java 名字 |
| `forwardSkin` | `true` | 独立服: 提供已验证玩家的皮肤及披风 |
| `premium-warning` | `true` | 独立服: 自助绑定时要求重复命令确认 |
| `kick-toggle` | `true` | 独立服切换后踢出玩家以便手动重连; 代理后端的排队转发也可能使用此项 |
| `require-declared-proxy-policy` | `false` | 代理后端: 要求代理提供登录策略; 升级代理并确认支持后再开启 |
| `respectIpLimit` | `false` | 在本端遵守 AuthMe 每 IP 注册限制, 代理后端也生效 |
| `useProxyAgnosticResolver` | `true` | 独立服: 验证会话时不传 IP, 适用于 DNAT 等环境 |
| `verifyClientKeys` | `false` | 独立服: 为适用客户端检查公钥签名, 不代替正版验证 |

## 连接限制

`anti-bot` 仅在独立服生效, 代理后端请修改代理配置. 超过限制会拒绝连接, 不替代防火墙.

| 选项 | 默认值 | 作用 |
| --- | --- | --- |
| `anti-bot.enabled` | `true` | 启用连接限制 |
| `anti-bot.connections` | `600` | 全局窗口连接数 |
| `anti-bot.expire` | `10` | 全局窗口, 分钟 |
| `anti-bot.per-ip-connections` | `20` | 每 IP 长窗口连接数 |
| `anti-bot.per-ip-expire` | `5` | 每 IP 长窗口, 分钟 |
| `anti-bot.burst-limit` | `10` | 每 IP 短窗口连接数 |
| `anti-bot.burst-window` | `10` | 短窗口, 秒 |
| `anti-bot.ban-duration` | `5` | 临时 IP 封禁, 分钟 |
| `anti-bot.trusted-ips` | 空 | 不受 FLP 连接限制的 IP 列表 |

## Mojang 与 Floodgate

| 选项 | 默认值 | 作用 |
| --- | --- | --- |
| `mojang-retry-count` | `3` | 独立服: 请求总尝试次数 |
| `mojang-retry-delay` | `500` | 独立服: 重试基础等待时间, 毫秒 |
| `ip-addresses` | 空 | 本地请求出口地址; 代理后端仅适用部分 Paper 查询 |
| `mojang-request-limit` | `600` | 每 10 分钟直连名字查询数, 范围 0 到 600; 0 必须配置 HTTP 代理 |
| `proxies` | 空 | Mojang HTTP 请求代理列表, 例如 `host:port` |
| `autoLoginFloodgate` | `false` | 独立服: 允许受支持的 Floodgate 自动登录 |
| `autoRegisterFloodgate` | `false` | 独立服: 允许 Floodgate 自动注册, 还要求允许自动登录 |
| `allowFloodgateNameConflict` | `false` | 独立服: 允许符合规则的未关联 Bedrock 名字与正版 Java 名字冲突 |

`autoLoginFloodgate` 和 `autoRegisterFloodgate` 接受 `false`, `true`, `linked` 或 `no-conflict`; 支持范围见 [Geyser 与 Floodgate](../integrations/bedrock.md). 代理后端的 Floodgate 策略由代理决定. `ip-addresses`, `mojang-request-limit` 和 `proxies` 在代理后端仅供适用的 Paper 查询路径使用.

## FLP 数据库

这些选项仅用于独立服的 FLP 名单; 代理模式使用代理数据库. 认证插件账号是另一份数据库.

| 选项 | 默认值 | 作用 |
| --- | --- | --- |
| `driver` | `sqlite` | `sqlite` 或 `mysql` |
| `database` | `{pluginDir}/FastLogin.db` | SQLite 文件路径; MySQL 时为数据库名 |
| `host` | `127.0.0.1` | MySQL 主机 |
| `port` | `3306` | MySQL 端口 |
| `username` | `myUser` | MySQL 用户名, 使用前修改 |
| `password` | `myPassword` | MySQL 密码, 使用前修改并保密 |
| `timeout` | `30` | 获取连接超时, 秒 |
| `lifetime` | `1800` | 连接最长存活时间, 秒; 0 不限制, 小于 300 的正值提升到 300 |
| `useSSL` | `false` | 启用 MySQL SSL 路径 |
| `sslMode` | `Required` | SSL 路径使用的驱动模式 |
| `allowPublicKeyRetrieval` | `false` | SSL 路径的 MySQL 公钥获取选项 |
| `ServerRSAPublicKeyFile` | 空 | 可选的本地 RSA 公钥文件 |

切换数据库前先备份和安排迁移, 修改配置不会自动迁移 SQLite 数据. 驱动及连接设置见[数据库配置](database.md).
