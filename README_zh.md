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
- **`fldelete` 重写** — 本地化消息、premium 玩家保护、BungeeCord 支持。
- **日志可读性** — 人类可读的登录流程消息替代原始包名输出。

## 快速开始

**Spigot / Paper：** 安装 ProtocolLib → 将 `FastLoginPlusBukkit.jar` 放入 `plugins/` → 设置 `online-mode=false`

**BungeeCord / Velocity：** 在代理和后端都安装 → 配置 `allowed-proxies.txt` → 启用 IP 转发 → 两端都设 `online-mode=false` → [完整指南](https://github.com/Hayston1001/FastLoginPlus/wiki)

详细安装步骤请参阅 [FastLogin 安装指南](https://github.com/TuxCoding/FastLogin#how-to-install)。

## 命令与权限

| 命令 | 说明 | 权限 | 默认值 |
|------|------|------|--------|
| `/premium [玩家]` | 标记为正版 | `fastloginplus.bukkit.command.premium` | true |
| `/cracked [玩家]` | 标记为离线 | `fastloginplus.bukkit.command.cracked` | op |
| `/fldelete <玩家>` | 删除玩家记录 | `fastloginplus.bukkit.command.delete` | op |

添加 `.other` 后缀可操作其他玩家（默认：op）。

> **注意：** 检测到 AuthMe 6.0+ 时，`/premium` 和 `/cracked` 会注册为 `/flp` 子命令（如 `/flp premium`），避免命令冲突。`/fldelete` 不受影响。

## PlaceholderAPI

导出 `%fastloginplus_status%` — 可选值：`Premium`、`Cracked`、`Unknown`。

## 许可证

[MIT](LICENSE) · 原作者：[games647](https://github.com/TuxCoding/FastLogin) · 维护者：[Hayston](https://github.com/Hayston1001)
