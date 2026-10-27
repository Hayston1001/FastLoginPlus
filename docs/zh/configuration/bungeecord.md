# BungeeCord 配置

文件: BungeeCord 代理的 `plugins/fastloginplus/config.yml`. 停服修改并重启代理. BungeeCord 和 Velocity 使用相同的 FLP 配置模板.

FLP 登录策略和名单位于代理; 后端认证插件的账号和密码仍在后端管理.

数据库方面, BungeeCord 内置 SQLite 驱动, **不内置 MariaDB 驱动**. 使用 MySQL/MariaDB 前, 请确认有与代理 Java 版本兼容的驱动, 见[数据库配置](database.md).

## 通用和登录

| 选项 | 默认值 | 作用 |
| --- | --- | --- |
| `language` | `en` | 代理消息语言, 支持 `en`, `zh` 及自定义文件 |
| `debug` | `false` | 输出排障日志 |
| `check-update` | `true` | 检查新版本, 不自动安装 |
| `update-check-interval` | `24` | 检查间隔, 小时 |
| `autoRegister` | `true` | 选择新名字的正版验证路径, 不是注册总开关 |
| `autoLogin` | `true` | 允许符合条件的玩家免密登录 |
| `auto-register-unknown` | `true` | 允许为已验证且没有账号的 Java 玩家创建账号 |
| `premiumUuid` | `true` | 使用正版 UUID; `false` 使用离线 UUID |
| `nameChangeCheck` | `true` | 检查正版改名, 不迁移玩家数据 |
| `secondAttemptCracked` | `false` | 符合条件的验证失败后允许后续使用密码路径 |
| `offline-whitelist` | `false` | 拒绝名单中未知的离线 Java 名字 |
| `forwardSkin` | `true` | 向后端转发已验证玩家的皮肤和披风 |
| `premium-warning` | `true` | 自助绑定时要求再次确认 |
| `kick-toggle` | `true` | 适用切换后踢出, 玩家手动重连 |
| `verify-backend-messages` | `true` | 检查后端账号管理消息; 仅在受控旧版本升级时临时关闭 |

## 连接限制

超限时拒绝连接, 不能代替防火墙. 调整前先检查真实 IP 转发.

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
| `mojang-retry-count` | `3` | 名字查询总尝试次数, 不控制代理原生会话验证 |
| `mojang-retry-delay` | `500` | 基础重试等待时间, 毫秒 |
| `ip-addresses` | 空 | 本地请求出口地址 |
| `mojang-request-limit` | `600` | 每 10 分钟直连名字查询数; 0 要求配置 HTTP 代理 |
| `proxies` | 空 | Mojang HTTP 请求代理, 例如 `host:port` |
| `autoLoginFloodgate` | `false` | 允许受支持的 Floodgate 自动登录 |
| `autoRegisterFloodgate` | `false` | 允许自动注册, 同时要求允许自动登录 |
| `allowFloodgateNameConflict` | `false` | 允许符合规则的未关联 Bedrock 名字冲突 |

两个 Floodgate 自动选项接受 `false`, `true`, `linked`, `no-conflict`; 支持范围见 [Geyser 与 Floodgate](../integrations/bedrock.md).

## FLP 数据库

此数据库保存代理 FLP 名单, 不替代后端认证账号数据库. 切换前备份并安排迁移; 配置更改不会自动迁移 SQLite.

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
