# FastLoginPlus

[English](README.md)

> **[FastLogin](https://github.com/TuxCoding/FastLogin) 的活跃维护分支** — 在离线模式服务器上自动检测并登录 Minecraft 正版账号。

基础功能、平台支持、技术原理等请参阅 [FastLogin 自述文件](https://github.com/TuxCoding/FastLogin)。

## 改进之处

- **AuthMe 6.0 兼容** — 自动检测 AuthMe 版本，适配正版流程和命令命名空间（`/flp`），无需用户配置。
- **离线白名单** — 阻止未知离线玩家，正版玩家通过 Mojang API 自动放行。替代上游的 `switchMode`（该功能会误踢首次加入的正版玩家）。
- **多语言** — 内置中英文，支持自定义语言文件，配置注释双语。
- **SQLite 并发优化** — WAL 模式、busy timeout、`ReentrantLock` 线程安全。
- **会话验证重试** — Mojang 验证遇到网络错误时自动重试，而非直接失败。
- **SkinsRestorer 兼容** — 不再覆盖 SkinsRestorer 设置的皮肤。
- **`/flp delete` 重写** — 本地化消息、premium 玩家保护、BungeeCord 支持。
- **日志可读性** — 人类可读的登录流程消息替代原始包名输出。

## 快速开始

**Spigot / Paper：** 安装 ProtocolLib → 将 `FastLoginPlusBukkit.jar` 放入 `plugins/` → 设置 `online-mode=false`

**BungeeCord / Velocity：** 在代理和后端都安装 → 配置 `allowed-proxies.txt` → 启用 IP 转发 → 两端都设 `online-mode=false` → [完整指南](https://github.com/Hayston1001/FastLoginPlus/wiki)

详细安装步骤请参阅 [FastLogin 安装指南](https://github.com/TuxCoding/FastLogin#how-to-install)。

## 基岩版玩家支持（Geyser / Floodgate）

FastLoginPlus 通过 [Geyser](https://geysermc.org/) 支持基岩版玩家加入离线模式 Java 服务器。

- **仅 Geyser** — 基岩玩家无需 Xbox 认证即可加入。FLP 将其视为普通 Java 玩家；若用户名与正版 Java 账号匹配，会触发正版自动登录。
- **Geyser + [Floodgate](https://geysermc.org/floodgate/)**（推荐） — 基岩玩家通过 Xbox Live 认证，用户名自动添加前缀（如 `Steve` → `.Steve`），避免 FLP 将基岩玩家误判为正版 Java 账号，同时防止两个平台的用户名冲突。

> **建议：** 在 Geyser 基础上额外安装 Floodgate，以获得更好的安全性和身份隔离。FLP 不强制要求 Floodgate，但在 Java 与基岩玩家共存的服务器上强烈推荐使用。

> **版本要求：** Geyser 运行需要 **Java 21+**。Geyser-Spigot 要求 Paper/Spigot 服务器版本在 **1.20.5 或以上**。低于 1.20.5 的服务器仍然可以通过 [ViaVersion](https://viaversion.com/) 使用 Geyser——在后端安装 ViaVersion 并通过代理（Velocity/BungeeCord）运行 Geyser，或直接搭建 Geyser-Standalone 配合 ViaVersion。ViaVersion 让服务器接受新版本 Java 客户端连接，Geyser 则以此为翻译目标实现基岩版接入。详见 [Geyser 支持的版本](https://geysermc.org/wiki/geyser/supported-versions/)。

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
