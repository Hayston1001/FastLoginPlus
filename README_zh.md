# FastLoginPlus

[English→](README.md)

> **在离线模式 Minecraft 服务器上自动检测并登录正版玩家** — 无需密码，无需客户端 Mod。基于 [FastLogin](https://github.com/TuxCoding/FastLogin) 的活跃维护分支。

许多 Minecraft 服务器运行在"离线模式"（不走 Mojang 认证）以允许盗版客户端加入，但这迫使所有玩家——包括已购游戏的正版玩家——每次进入都要输密码。FastLoginPlus 在登录时通过 Mojang API 检查玩家身份：如果是正版，直接跳过登录插件，自动使用正版 UUID 和皮肤。

## 功能

### 核心功能（来自 FastLogin）

* 通过 Mojang API 自动检测正版账号 — 跳过登录插件
* 正版 UUID 和皮肤转发
* 自动注册新正版玩家
* BungeeCord / Velocity 代理支持
* 通过 Geyser / Floodgate 支持基岩版玩家

### 改进之处（FastLoginPlus 新增）

* **AuthMe 6.0 兼容** — 自动检测 AuthMe 版本，无需用户配置
* **离线白名单** — 阻止未知离线玩家，正版玩家通过 Mojang API 自动放行。替代上游的 `switchMode`（该功能会误踢首次加入的正版玩家）
* **多层反机器人** — 每 IP 速率限制、突发检测、临时封禁、可信 IP 白名单，以及 `FastLoginAntiBotEvent` 供其他插件集成
* **Folia 支持** — 独立模块，使用 Folia 兼容的调度器（`Entity.getScheduler()`、`Bukkit.getAsyncScheduler()`）
* **自动更新检查** — 启动时及定期检查 GitHub Releases，有新版本时游戏内通知 OP
* **多语言** — 内置中英文，支持自定义语言文件，配置注释双语
* **代理端 SQLite 支持** — BungeeCord 和 Velocity 内置 SQLite JDBC 驱动，适用于单代理小型服。上游仅支持 MySQL/MariaDB
* **会话验证重试** — Mojang 验证遇到网络错误时自动重试，而非直接失败
* **SkinsRestorer 兼容** — 不再覆盖 SkinsRestorer 设置的皮肤
* **日志可读性** — 人类可读的登录流程消息替代原始包名输出

## 快速开始

**Spigot / Paper：** 安装 ProtocolLib → 将 `FastLoginPlusBukkit.jar` 放入 `plugins/` → 设置 `online-mode=false`

**Folia：** 将 `FastLoginPlusFolia.jar` 放入 `plugins/` → 设置 `online-mode=false`

**BungeeCord / Velocity：** 在代理和后端都安装 → 配置 `allowed-proxies.txt` → 启用 IP 转发 → 两端都设 `online-mode=false`

## 环境要求

| 平台 | Java | 备注 |
|------|------|------|
| Spigot / Paper | 8+ | 需要 [ProtocolLib 5.3+](https://www.spigotmc.org/resources/protocollib.1997/) 或 [ProtocolSupport](https://www.spigotmc.org/resources/protocolsupport.7201/) |
| Folia | 17+ | 需要 ProtocolLib 5.3+ |
| BungeeCord / Waterfall | 17+ | — |
| Velocity | 17+ | — |

需要后端安装登录插件（如 AuthMe、LoginSecurity、CrazyLogin）。[完整列表](https://github.com/TuxCoding/FastLogin#supported-auth-plugins)。

## 命令与权限

| 命令 | 说明 | 权限 | 默认值 |
|------|------|------|--------|
| `/flp premium [玩家]` | 标记为正版 | `fastloginplus.bukkit.command.premium` | true |
| `/flp cracked [玩家]` | 标记为离线 | `fastloginplus.bukkit.command.cracked` | op |
| `/flp delete <玩家>` | 删除玩家记录 | `fastloginplus.bukkit.command.delete` | op |

添加 `.other` 后缀可操作其他玩家（默认：op）。

## PlaceholderAPI

| 占位符 | 可选值 | 说明 |
|---|---|---|
| `%fastloginplus_status%` | `Premium`、`Cracked`、`Unknown` | 认证状态 |
| `%fastloginplus_is_premium%` | `true`、`false` | 是否通过正版验证 |
| `%fastloginplus_floodgate%` | `Java`、`Bedrock`、`Linked`、`Unknown` | 连接平台（Java 版或通过 Geyser/Floodgate 的基岩版） |

## 许可证

[MIT](LICENSE) · 原作者：[games647](https://github.com/TuxCoding/FastLogin) · 维护者：[Hayston](https://github.com/Hayston1001)
