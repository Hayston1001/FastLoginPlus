# Folia 配置

文件: Folia 的 `plugins/fastloginplus/config.yml`. 停服修改并重启. Folia 与 Bukkit 使用相同的配置模板; 下列每项的默认值和作用相同. 独立服和代理后端的生效范围也相同.

在 Folia 上执行账号命令时使用 `fastloginplus.folia.command.*` 权限, 不使用 Bukkit 权限前缀.

Folia 位于代理后面时, 登录策略, 名单, 连接限制和 FLP 数据库以代理配置为准; `respectIpLimit` 和本地语言等仍在后端生效. 代理选择 [Velocity](velocity.md) 或 [BungeeCord](bungeecord.md).

## 通用和登录

| 选项 | 默认值 | 作用 |
| --- | --- | --- |
| `language` | `en` | 本地消息语言, 支持 `en`, `zh` 及自定义文件 |
| `debug` | `false` | 输出排障日志 |
| `check-update` | `true` | 检查新版本, 不自动安装 |
| `update-check-interval` | `24` | 检查间隔, 小时 |
| `autoRegister` | `true` | 独立服: 选择新名字的正版验证路径 |
| `autoLogin` | `true` | 独立服: 允许符合条件的玩家免密登录 |
| `auto-register-unknown` | `true` | 独立服: 允许为已验证且没有账号的 Java 玩家创建账号 |
| `premiumUuid` | `true` | 独立服: 使用正版 UUID; `false` 使用离线 UUID |
| `nameChangeCheck` | `true` | 独立服: 检查正版改名, 不迁移玩家数据 |
| `secondAttemptCracked` | `false` | 独立服: 符合条件的验证失败后允许后续使用密码路径 |
| `offline-whitelist` | `false` | 独立服: 拒绝名单中未知的离线 Java 名字 |
| `forwardSkin` | `true` | 独立服: 提供已验证玩家的皮肤和披风 |
| `premium-warning` | `true` | 独立服: 自助绑定时要求再次确认 |
| `kick-toggle` | `true` | 独立服切换后踢出; 代理后端的排队转发也可能使用此项 |
| `require-declared-proxy-policy` | `false` | 代理后端要求代理提供登录策略, 代理升级后再开启 |
| `respectIpLimit` | `false` | 遵守本地 AuthMe 的每 IP 注册限制 |
| `useProxyAgnosticResolver` | `true` | 独立服: 会话验证不传 IP |
| `verifyClientKeys` | `false` | 独立服: 检查适用客户端公钥签名 |

## 连接限制

仅独立服在本机生效; 代理部署在代理配置. 超限时拒绝连接, 不能代替防火墙.

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
| `anti-bot.trusted-ips` | 空 | 跳过 FLP 连接限制的 IP 列表 |

## Mojang 与 Floodgate

| 选项 | 默认值 | 作用 |
| --- | --- | --- |
| `mojang-retry-count` | `3` | 独立服: 请求总尝试次数 |
| `mojang-retry-delay` | `500` | 独立服: 基础重试等待时间, 毫秒 |
| `ip-addresses` | 空 | 本地请求出口地址 |
| `mojang-request-limit` | `600` | 每 10 分钟直连名字查询数; 0 要求配置 HTTP 代理 |
| `proxies` | 空 | Mojang HTTP 请求代理, 例如 `host:port` |
| `autoLoginFloodgate` | `false` | 独立服: 允许受支持的 Floodgate 自动登录 |
| `autoRegisterFloodgate` | `false` | 独立服: 允许自动注册, 同时要求允许自动登录 |
| `allowFloodgateNameConflict` | `false` | 独立服: 允许符合规则的未关联 Bedrock 名字冲突 |

两个 Floodgate 自动选项接受 `false`, `true`, `linked`, `no-conflict`; 范围见 [Geyser 与 Floodgate](../integrations/bedrock.md). 代理后端的 Floodgate 策略由代理决定; `ip-addresses`, `mojang-request-limit`, `proxies` 仍可用于适用的 Paper 查询路径.

## FLP 数据库

仅独立服使用此处的 FLP 名单数据库; 认证插件账号是另一份数据库.

| 选项 | 默认值 | 作用 |
| --- | --- | --- |
| `driver` | `sqlite` | `sqlite` 或 `mysql` |
| `database` | `{pluginDir}/FastLogin.db` | SQLite 文件路径或 MySQL 数据库名 |
| `host` | `127.0.0.1` | MySQL 主机 |
| `port` | `3306` | MySQL 端口 |
| `username` | `myUser` | MySQL 用户名, 使用前修改 |
| `password` | `myPassword` | MySQL 密码, 使用前修改并保密 |
| `timeout` | `30` | 获取连接超时, 秒 |
| `lifetime` | `1800` | 连接最长存活时间, 秒; 0 不限制, 小于 300 的正值提升到 300 |
| `useSSL` | `false` | 启用 MySQL SSL 路径 |
| `sslMode` | `Required` | SSL 路径使用的驱动模式 |
| `allowPublicKeyRetrieval` | `false` | SSL 路径的 MySQL 公钥获取选项 |
| `ServerRSAPublicKeyFile` | 空 | 可选本地 RSA 公钥文件 |

切换前备份并安排迁移; 配置更改不会自动迁移 SQLite. 详见[数据库配置](database.md).
