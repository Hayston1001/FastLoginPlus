# FastLoginPlus

[English](README.md)

> **[FastLogin](https://github.com/TuxCoding/FastLogin) 的活跃维护分支**，持续更新、修复 Bug、增加功能。

FastLoginPlus 自动检测 Minecraft 玩家是否拥有正版（付费）账号。如果是，可以跳过离线模式认证——无需输入密码。

## 功能特性

* 自动检测并登录正版（付费）账号
* 自动注册新正版玩家
* 正版 UUID 支持 & 皮肤转发
* 用户名变更检测并自动更新数据库
* 多平台支持：Bukkit (Spigot/Paper) / BungeeCord / Velocity
* 通过 Floodgate / Geyser 支持基岩版玩家
* 内置英文 & 中文语言文件，支持自定义语言
* PlaceholderAPI 集成
* 异步操作，高性能
* 无需客户端 Mod

## FastLoginPlus 的改进

相比原版 FastLogin，本分支包含：

* **SQLite WAL 模式** — 写前日志，代理架构下多线程读写不再互相阻塞
* **SQLite busy timeout** — 5 秒等待机制，避免瞬间 `SQLITE_BUSY` 报错
* **线程安全的 SQLite 操作** — `ReentrantLock` 保护所有 profile 读写操作
* **switchMode Bug 修复** — 开启 `switchMode` 后首次加入的正版玩家不再被错误踢出（[#1359](https://github.com/TuxCoding/FastLogin/issues/1359)）
* **fldelete 命令增强** — 本地化消息、正版玩家保护、BungeeCord 支持
* **多语言系统** — 内置 `en`/`zh`，配置驱动，启动时自动补全缺失键值
* **配置文件中英双语** — `config.yml` 注释同时提供英文和中文

## 环境要求

* **Java**：8+ (Spigot)，17+ (BungeeCord / Velocity)，推荐 21+
* **服务器**离线模式（`online-mode=false`）
* **Spigot**（或 Paper）1.8.8+，需安装 [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)（5.3+）或 [ProtocolSupport](https://www.spigotmc.org/resources/protocolsupport.7201/)
* **BungeeCord**（或 Waterfall）/ **Velocity** 代理
* 一个登录插件（见下方）

### 支持的登录插件

#### Spigot / Paper
* [AuthMe](https://dev.bukkit.org/bukkit-plugins/authme-reloaded/)（5.x）
* [CrazyLogin](https://dev.bukkit.org/bukkit-plugins/crazylogin/)
* [LoginSecurity](https://dev.bukkit.org/bukkit-plugins/loginsecurity/)
* [LogIt](https://github.com/games647/LogIt)
* [UltraAuth](https://dev.bukkit.org/bukkit-plugins/ultraauth-aa/)
* [xAuth](https://dev.bukkit.org/bukkit-plugins/xauth/)
* [Passky](https://github.com/Passky)

#### BungeeCord / Waterfall
* [BungeeAuth](https://www.spigotmc.org/resources/bungeeauth.493/)

## 安装

### Spigot / Paper

1. 安装 ProtocolLib 或 ProtocolSupport
2. 下载 `FastLoginPlusBukkit.jar` 放入 `plugins/` 文件夹
3. 在 `server.properties` 中设置 `online-mode=false`

### BungeeCord / Waterfall 或 Velocity

需要在**代理和后端服务器**上都安装此插件：

1. 在后端服务器配置中启用代理支持（`spigot.yml` 或 `paper.yml`）
2. 重启后端服务器
3. 在 FastLoginPlus 插件文件夹中配置 `allowed-proxies.txt`：
   - **BungeeCord**：填入 BungeeCord 配置中的 `stats`-id
   - **Velocity**：插件会自动生成 `proxyId.txt`
4. 在代理配置中启用 IP 转发
5. 在 `config.yml` 中配置数据库（BungeeCord 用 `mysql`；Velocity 用 `mariadb`）
6. 代理和后端都设置 `online-mode=false`
7. **务必**配置防火墙，确保后端服务器只能通过代理访问

## 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/premium [玩家]` | 标记自己（或指定玩家）为正版 | `fastloginplus.bukkit.command.premium` |
| `/cracked [玩家]` | 标记自己（或指定玩家）为离线 | `fastloginplus.bukkit.command.cracked` |
| `/fldelete <玩家>` | 从数据库中删除玩家记录 | `fastloginplus.bukkit.command.delete` |

## 权限

| 权限 | 说明 | 默认值 |
|------|------|--------|
| `fastloginplus.bukkit.command.premium` | 标记自己为正版 | true |
| `fastloginplus.bukkit.command.premium.other` | 标记他人为正版 | op |
| `fastloginplus.bukkit.command.cracked` | 标记自己为离线 | true |
| `fastloginplus.bukkit.command.cracked.other` | 标记他人为离线 | op |
| `fastloginplus.bukkit.command.delete` | 删除玩家记录 | op |

## PlaceholderAPI

在 Spigot 上，此插件导出 `%fastloginplus_status%`。可选值：`Premium`、`Cracked`、`Unknown`。

> 在 BungeeCord 环境中，玩家加入后状态可能短暂显示为 `Unknown`（几毫秒）。

## 语言支持

在 `config.yml` 中设置 `language`：

```yaml
language: en   # English
language: zh   # 中文
```

支持自定义语言——设置任意值（如 `language: ja`），插件会加载 `messages_ja.yml`。如果文件不存在，回退到英文。每次启动时自动补全缺失的键值。

## 网络请求

此插件会联系：
* `https://api.mojang.com` — UUID 查询，用于正版检测
* `https://sessionserver.mojang.com` — 账号所有权验证

## 许可证

本项目基于 [MIT 许可证](LICENSE)。

原作者：[games647](https://github.com/TuxCoding/FastLogin)。
分支维护：[Hayston](https://github.com/Hayston1001)。
