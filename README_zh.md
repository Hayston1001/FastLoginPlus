# FastLoginPlus

[English](README.md)

> **[FastLogin](https://github.com/TuxCoding/FastLogin) 的活跃维护分支**，持续修复 Bug、优化性能、增加功能。

在离线模式服务器上自动检测 Minecraft 正版（付费）账号——跳过认证，无需密码。

## 功能特性

* 自动检测、登录、注册正版账号
* 正版 UUID 和皮肤转发，用户名变更检测
* **离线白名单** — 阻止未知离线玩家，正版玩家通过 Mojang API 自动放行
* 多平台支持：Bukkit (Spigot/Paper) / BungeeCord / Velocity
* 通过 Floodgate / Geyser 支持基岩版
* SQLite WAL 模式 + 线程安全操作（ReentrantLock + busy timeout）
* 内置中英文，支持自定义语言，配置文件中英双语
* PlaceholderAPI 集成，全异步，无需客户端 Mod

## 环境要求

* **Java** 8+（Spigot），17+（BungeeCord / Velocity），推荐 21+
* 服务器**离线模式**（`online-mode=false`）
* [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)（5.3+）或 [ProtocolSupport](https://www.spigotmc.org/resources/protocolsupport.7201/)
* 登录插件：[AuthMe](https://dev.bukkit.org/bukkit-plugins/authme-reloaded/) · [LoginSecurity](https://dev.bukkit.org/bukkit-plugins/loginsecurity/) · [CrazyLogin](https://dev.bukkit.org/bukkit-plugins/crazylogin/) · [xAuth](https://dev.bukkit.org/bukkit-plugins/xauth/) · [Passky](https://github.com/Passky) · [BungeeAuth](https://www.spigotmc.org/resources/bungeeauth.493/)

## 快速开始

**Spigot / Paper：** 安装 ProtocolLib → 将 `FastLoginPlusBukkit.jar` 放入 `plugins/` → 设置 `online-mode=false`

**BungeeCord / Velocity：** 在代理和后端都安装 → 配置 `allowed-proxies.txt` → 启用 IP 转发 → 两端都设 `online-mode=false` → [完整指南](https://github.com/Hayston1001/FastLoginPlus/wiki)

## 命令与权限

| 命令 | 说明 | 权限 | 默认值 |
|------|------|------|--------|
| `/premium [玩家]` | 标记为正版 | `fastloginplus.bukkit.command.premium` | true |
| `/cracked [玩家]` | 标记为离线 | `fastloginplus.bukkit.command.cracked` | true |
| `/fldelete <玩家>` | 删除玩家记录 | `fastloginplus.bukkit.command.delete` | op |

添加 `.other` 后缀可操作其他玩家（默认：op）。

## PlaceholderAPI

导出 `%fastloginplus_status%` — 可选值：`Premium`、`Cracked`、`Unknown`。

## 许可证

[MIT](LICENSE) · 原作者：[games647](https://github.com/TuxCoding/FastLogin) · 维护者：[Hayston](https://github.com/Hayston1001)
