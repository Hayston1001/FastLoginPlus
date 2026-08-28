# FastLoginPlus

[English→](https://github.com/Hayston1001/FastLoginPlus#FastLoginPlus)

> **在离线模式 Minecraft 服务器上自动检测并登录正版玩家** — 无需密码, 无需客户端 Mod. 基于 [FastLogin](https://github.com/TuxCoding/FastLogin) 的活跃维护分支.

许多 Minecraft 服务器运行在"离线模式"(不走 Mojang 认证)以允许盗版客户端加入, 但这迫使所有玩家——包括已购游戏的正版玩家——每次进入都要输密码.FastLoginPlus 在登录时通过 Mojang API 检查玩家身份：如果是正版, 直接跳过登录插件, 自动使用正版 UUID 和皮肤.

## 功能

### 核心功能(来自 FastLogin)

* 通过 Mojang API 自动检测正版账号 — 跳过登录插件
* 正版 UUID 和皮肤转发
* 自动注册新正版玩家
* BungeeCord/Velocity 代理支持
* 通过 Geyser/Floodgate 支持基岩版玩家

### 改进之处(FastLoginPlus 新增)

* **[AuthMeReloaded](https://modrinth.com/plugin/authmereloaded) 6.0 兼容** — 自动检测 AuthMeReloaded 版本, 无需用户配置
* **离线白名单** — 阻止未知离线玩家, 正版玩家通过 Mojang API 自动放行.替代上游的 `switchMode`(该功能会误踢首次加入的正版玩家)
* **多层反机器人** — 每 IP 速率限制、突发检测、临时封禁、可信 IP 白名单, 以及 `FastLoginAntiBotEvent` 供其他插件集成
* **[Folia](https://papermc.io/downloads/folia) 支持** — 独立模块, 使用 Folia 兼容的调度器
* **自动更新检查** — 启动时及定期检查 GitHub Releases, 有新版本时游戏内通知 OP
* **多语言** — 内置中英文, 支持自定义语言文件
* **分平台配置模板** — Bukkit/Folia 与 BungeeCord/Velocity 各自生成专属的 config 文件: 代理端使用不含后端专属键的精简模板, 后端模板的注释标明代理模式下失效的配置
* **代理端 SQLite 支持** — BungeeCord 和 Velocity 内置 SQLite JDBC 驱动, 适用于单代理小型服.上游仅支持 MySQL/MariaDB
* **会话验证重试** — Mojang 验证遇到网络错误时自动重试, 而非直接失败
* **[SkinsRestorer](https://modrinth.com/plugin/skinsrestorer) 兼容** — 不再覆盖 SkinsRestorer 设置的皮肤
* **日志可读性** — 人类可读的登录流程消息替代原始包名输出

## 快速开始

**Spigot/Paper：** 安装 ProtocolLib → 将 `FastLoginPlusBukkit.jar` 放入 `plugins/` → 设置 `online-mode=false`

**Folia：** 将 `FastLoginPlusFolia.jar` 放入 `plugins/` → 设置 `online-mode=false`

### 代理配置

使用代理 (BungeeCord 或 Velocity) 时，代理必须正确配置玩家信息转发，FLP 才能把登录指令送达后端。

<details>
<summary>代理 ID 配置(点击展开)</summary>

后端只接受来自受信任代理的登录指令. 每个代理有一个唯一 UUID, 需要加入后端的白名单：

- **Velocity** — FLP 首次启动时自动生成 UUID 到 `plugins/fastloginplus/proxyId.txt`. 从该文件复制 UUID. 
- **BungeeCord** — 使用 BungeeCord 自身的实例 UUID, 在 `bungee/config.yml` 的 `connection_uuid` 字段中. 

将 UUID 粘贴到每个后端服务器的 `plugins/fastloginplus/allowed-proxies.txt` 中, 每行一个 UUID. 添加后重启后端. 

</details>

#### Velocity

| 配置 | 值 | 原因 |
|------|-----|------|
| `velocity.toml` → `player-info-forwarding-mode` | `modern` | **必须配置**。不配的话 Velocity 不转发 UUID、皮肤、IP —— FLP 的插件消息根本到不了后端。 |
| `velocity.toml` → `online-mode` | `false` | FLP 通过 `forceOnlineMode()` 逐连接控制认证，代理端不应默认开启在线模式。 |
| 后端 `server.properties` → `online-mode` | `false` | 代理已负责认证，后端不能再做。 |
| 后端 `paper-global.yml` → `proxies.velocity.online-mode` | `false` | 必须与 `velocity.toml` 的 `online-mode`。 |

#### BungeeCord

| 配置 | 值 | 原因 |
|------|-----|------|
| `config.yml` → `ip_forward` | `true` | **必须配置**。不配的话 BungeeCord 不转发 UUID、皮肤、IP —— FLP 的插件消息根本到不了后端。 |
| `config.yml` → `online_mode` | `false` | FLP 通过 `connection.setOnlineMode(true)` 逐连接开启，代理端不应默认开启在线模式。 |
| 后端 `server.properties` → `online-mode` | `false` | 代理已负责认证，后端不能再做。 |

### 数据库存储

**单端模式**(无代理): 数据库(默认为 `FastLogin.db`)存储在每个后端服务器的 `plugins/fastloginplus/` 目录下.

**代理模式**(BungeeCord/Velocity)：数据库**仅存储在代理端**. 后端服务器不会创建数据库文件——后端只负责接收代理通过插件消息发来的登录/注册指令并执行. 后端的 `/flp premium` 和 `/flp cracked` 命令会转发到代理, 由代理处理所有数据库读写操作. 

### 配置模板

FLP 内置**两套配置模板**；每个平台根据自身角色生成 `config.yml`：

| 模板 | 适用平台 | 内容 |
|------|---------|------|
| `config.yml`(后端) | Bukkit、Folia | 全部配置项。注释标明哪些项在**代理子服模式**下不生效(或仅部分生效)——例如 `database`、`anti-bot`、Floodgate 相关配置在代理子服上无效, 因为这些功能由代理端负责。 |
| `config-proxy.yml`(代理端) | BungeeCord、Velocity | 仅代理端相关配置项。不含后端专属键(`verifyClientKeys`、`respectIpLimit`), 注释描述代理端的职责(决策方：Mojang API 查询、数据库、转发强制登录指令)。 |

磁盘上的文件名始终是 `config.yml`。在代理端与后端之间拷贝配置文件是安全的：每次启动时各平台会按自己的模板重建文件结构并保留你修改过的值, 新模板中不存在的键会被直接移除(它们在该平台本就无效)。

## 环境要求

| 平台 | Java | 备注 |
|------|------|------|
| Spigot/Paper | 8+ | 需要 [ProtocolLib 5.3+](https://www.spigotmc.org/resources/protocollib.1997/) 或 [ProtocolSupport](https://www.spigotmc.org/resources/protocolsupport.7201/) |
| Folia | 21+ | 需要 ProtocolLib 5.3+ |
| BungeeCord/Waterfall | 17+ | — |
| Velocity | 17+ | — |

需要后端安装登录插件(如 AuthMe、LoginSecurity、CrazyLogin) [完整列表→](https://github.com/TuxCoding/FastLogin#supported-auth-plugins)

## [AuthMeReloaded](https://modrinth.com/plugin/authmereloaded) 5.x / 6.0 支持

FastLoginPlus 同时支持 AuthMeReloaded 5.x 和 6.0. AuthMeReloaded 6.0 新增了 **preJoin 对话框(Paper) 以及 enablePremium 配置**, FLP 会自动启用 `enablePremium: true` 并注销 AuthMe 自带的正版验证监听器. 无需手动配置. 

## 基岩版玩家支持(Geyser/Floodgate)

FastLoginPlus 通过 [Geyser](https://geysermc.org/) 支持基岩版玩家加入离线模式 Java 服务器.

- **仅 Geyser** — 基岩玩家无需 Xbox 认证即可加入.FLP 将其视为普通 Java 玩家；若用户名与正版 Java 账号匹配, 会触发正版自动登录.
- **Geyser + [Floodgate](https://geysermc.org/floodgate/)**(推荐) — 基岩玩家通过 Xbox Live 认证, 用户名自动添加前缀(如 `Steve` → `.Steve`), 避免 FLP 将基岩玩家误判为正版 Java 账号, 同时防止两个平台的用户名冲突.

> **建议：** 在 Geyser 基础上额外安装 Floodgate, 以获得更好的安全性和身份隔离.FLP 不强制要求 Floodgate, 但在 Java 与基岩玩家共存的服务器上强烈推荐使用.

> **版本要求：** Geyser 运行需要 **Java 21+**.Geyser-Spigot 要求 Paper/Spigot 服务器版本在 **1.20.5 或以上**.低于 1.20.5 的服务器仍然可以通过 [ViaVersion](https://viaversion.com/) 使用 Geyser——在后端安装 ViaVersion 并通过代理(Velocity/BungeeCord)运行 Geyser, 或直接搭建 Geyser-Standalone 配合 ViaVersion.ViaVersion 让服务器接受新版本 Java 客户端连接, Geyser 则以此为翻译目标实现基岩版接入.详见 [Geyser 支持的版本](https://geysermc.org/wiki/geyser/supported-versions/).

## 命令与权限

| 命令 | 说明 | 权限 | 默认值 |
|------|------|------|--------|
| `/flp premium [玩家]` | 标记为正版 | `fastloginplus.bukkit.command.premium` | true |
| `/flp cracked [玩家]` | 标记为离线 | `fastloginplus.bukkit.command.cracked` | op |
| `/flp delete <玩家>` | 删除玩家记录 | `fastloginplus.bukkit.command.delete` | op |

添加 `.other` 后缀可操作其他玩家(默认：op).

> 当玩家执行指令 `/flp cracked` 从正版验证模式切换至离线模式时, FLP 会自动清除该玩家在 AuthMeReloaded 内的账号数据, 保证玩家重新加入服务器后可通过自行设置的密码正常登录. 若未执行该数据清理操作, 玩家再次进入服务器时 AuthMeReloaded 会强制要求登录; 但该玩家此前为正版账号时, FLP 已自动使用随机密码完成注册, 玩家本身并不知晓该密码.  
> 对于非 Authme 登录插件, FLP 暂时没有类似处理, 需要手动解决.

## PlaceholderAPI

| 占位符 | 可选值 | 说明 |
|---|---|---|
| `%fastloginplus_status%` | `Premium`、`Cracked`、`Unknown` | 认证状态 |
| `%fastloginplus_is_premium%` | `true`、`false` | 是否通过正版验证 |
| `%fastloginplus_floodgate%` | `Java`、`Bedrock`、`Linked`、`Unknown` | 连接平台(Java 版或通过 Geyser/Floodgate 的基岩版) |

## 许可证

[MIT](LICENSE) · 原作者：[games647](https://github.com/TuxCoding/FastLogin) · 维护者：[Hayston](https://github.com/Hayston1001)
