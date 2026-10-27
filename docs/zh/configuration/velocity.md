# Velocity 配置

文件: Velocity 代理的 `plugins/fastloginplus/config.yml`. 停服修改并重启代理. 此文件控制网络登录策略和 FLP 名单; 后端认证插件的账号和密码仍在后端管理.

## 通用与登录

| 选项 | 默认值 | 作用 |
| --- | --- | --- |
| `language` | `en` | 代理消息语言, 内置 `en`/`zh`, 也支持自定义文件 |
| `debug` | `false` | 输出排障日志 |
| `check-update` | `true` | 检查新版本, 不自动安装 |
| `update-check-interval` | `24` | 更新检查间隔, 小时 |
| `autoRegister` | `true` | 为符合条件的新名字选择正版验证路径, 不是所有注册的总开关 |
| `autoLogin` | `true` | 允许符合条件的已验证玩家免密登录 |
| `auto-register-unknown` | `true` | 允许为已验证但没有本地账号的 Java 玩家创建账号 |
| `premiumUuid` | `true` | 使用正版 UUID; `false` 使用离线 UUID. 修改前备份玩家数据 |
| `nameChangeCheck` | `true` | 检查正版改名并更新 FLP 记录, 不迁移玩家数据 |
| `secondAttemptCracked` | `false` | 符合条件的验证失败后允许后续使用密码路径 |
| `offline-whitelist` | `false` | 拒绝 FLP 名单中未知的离线 Java 名字 |
| `forwardSkin` | `true` | 向后端转发已验证玩家的皮肤和披风 |
| `premium-warning` | `true` | 自助绑定时要求重复命令确认 |
| `kick-toggle` | `true` | 适用的账号切换后踢出玩家, 玩家需手动重连 |
| `verify-backend-messages` | `true` | 检查后端账号管理消息, 仅在受控旧版本升级时临时关闭 |

## 连接限制

超过限制会拒绝连接; 这些设置不替代防火墙. 确认真实 IP 转发正确后再调整限制.

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
| `mojang-retry-count` | `3` | 名字查询总尝试次数, 不控制代理原生会话验证 |
| `mojang-retry-delay` | `500` | 名字查询重试基础等待时间, 毫秒 |
| `ip-addresses` | 空 | 本地请求出口地址 |
| `mojang-request-limit` | `600` | 每 10 分钟直连名字查询数, 范围 0 到 600; 0 必须配置 HTTP 代理 |
| `proxies` | 空 | Mojang HTTP 请求代理列表, 例如 `host:port` |
| `autoLoginFloodgate` | `false` | 允许受支持的 Floodgate 自动登录 |
| `autoRegisterFloodgate` | `false` | 允许 Floodgate 自动注册, 还要求允许自动登录 |
| `allowFloodgateNameConflict` | `false` | 允许符合规则的未关联 Bedrock 名字与正版 Java 名字冲突 |

`autoLoginFloodgate` 和 `autoRegisterFloodgate` 接受 `false`, `true`, `linked` 或 `no-conflict`. Fabric 的 Bedrock 支持范围更窄, 详见 [Geyser 与 Floodgate](../integrations/bedrock.md).

## FLP 数据库

此数据库保存代理的 FLP 名单, 不替代后端认证数据库. 切换数据库前备份和安排迁移; 修改配置不会自动迁移 SQLite 数据.

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

驱动和连接设置见[数据库配置](database.md). Velocity 内置 SQLite 和 MariaDB 驱动.
