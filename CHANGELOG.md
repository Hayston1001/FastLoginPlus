# FastLoginPlus Changelog

## v0.3.1 (Hotfix)

### Bug Fix(Critical) / Bug 修复(紧急)

- Fix `ClassCastException: Integer cannot be cast to Long` on login — SnakeYAML parses small numeric config values (e.g. `mojang-retry-delay: 500`) as `Integer`, but the code directly cast to `(long)` which crashes. Use `Number.longValue()`/`intValue()` for safe conversion across all numeric config reads

- 修复登录时 `ClassCastException: Integer cannot be cast to Long` — SnakeYAML 将较小的数值配置（如 `mojang-retry-delay: 500`）解析为 `Integer`，但代码直接强转 `(long)` 导致崩溃。所有数值配置读取改用 `Number.longValue()`/`intValue()` 安全转换

## v0.3.0

### Bug Fixes(Major) / Bug 修复(主要)

- Anti-bot module audit — 6 bug fixes: clock jump back no longer throws in TickingRateLimiter, batch expire stale records, compareTo uses correct expireTime, global rate limit checked before per-IP, periodic cleanup every 100 connections, sanitize usernames in log messages
- Fix `forwardSkin: false` not working on Paper — PaperCacheListener now checks config before setting skin
- Fix SkinsRestorer skin overwritten by Paper filledProfileCache — set empty placeholder textures to prevent `complete(true)` pulling stale skin
- Guard against null `floodgate_data_handler` in ProtocolLib pipeline — prevent NPE if Floodgate renames/removes the handler

- 反机器人模块审计 — 6 个 bug 修复：TickingRateLimiter 时钟回退不再抛异常、批量过期陈旧记录、compareTo 使用正确的 expireTime、全局限制在每 IP 限制之前检查、每 100 连接定期清理、日志中用户名消毒
- 修复 Paper 上 `forwardSkin: false` 无效 — PaperCacheListener 现在在设置皮肤前检查配置
- 修复 SkinsRestorer 皮肤被 Paper filledProfileCache 覆盖 — 设置空占位纹理防止 `complete(true)` 拉取旧皮肤
- 防止 ProtocolLib pipeline 中 `floodgate_data_handler` 为 null — 避免 Floodgate 重命名/移除 handler 时 NPE

### Code Cleanup / 代码清理

- Remove 22 redundant default values from config `getXxx()` calls across all modules (ConfigRefresher guarantees keys exist)

- 移除 22 处 config `getXxx()` 调用中的冗余默认值（ConfigRefresher 保证键已存在）

### Session Retry Improvement / 会话重试改进

- Exponential backoff for Mojang session retry

- Mojang 会话重试改为指数退避

### Build / 构建

- Centralize 19 shared dependency versions into parent pom.xml properties
- Add FloodgateServiceTest (17 tests) and FloodgateManagementTest (15 tests)
- Upgrade maven-compiler-plugin 3.13.0 → 3.15.0

- 将 19 个共享依赖版本集中到父 pom.xml 属性
- 新增 FloodgateServiceTest（17 个测试）和 FloodgateManagementTest（15 个测试）
- maven-compiler-plugin 3.13.0 → 3.15.0

## v0.2.1

### Version Format Unification / 版本格式统一

- All plugin descriptors (plugin.yml, bungee.yml, velocity-plugin.json) now use `${revision}-${git.commit.id.abbrev}` format consistently
- Removed redundant `ManifestResourceTransformer` from bungee/velocity shade config
- Removed `META-INF/MANIFEST.MF` exclusion from shade filters that was causing MANIFEST loss

- 所有插件描述文件（plugin.yml、bungee.yml、velocity-plugin.json）现在统一使用 `${revision}-${git.commit.id.abbrev}` 格式
- 移除了 bungee/velocity shade 配置中多余的 `ManifestResourceTransformer`
- 移除了 shade filter 中导致 MANIFEST 丢失的 `META-INF/MANIFEST.MF` 排除规则

## v0.2.0

### Config Refresher / 配置刷新器

- Restore comments and keys in config.yml from the template each startup, keeping the config file aligned with the latest version and more portable

- 在每次启动时从模板恢复 config.yml 的注释和键,使得配置文件与最新版本一致,也更具可迁移性

### Log Output Optimization / 日志输出优化

- Add `debug: false` option to config.yml to reduce log verbosity

- 在 config.yml 中添加 `debug: false` 选项以减少日志冗余

### Git Hash in JAR Names / JAR 文件名加入 Git 哈希

- Include git commit hash in JAR file names and MANIFEST to make file versions clearer

- 在 JAR 文件名和 MANIFEST 中包含 git 提交哈希使得文件版本更明确

### Bug Fixes / Bug 修复

- Fix `sslMode` and `allowPublicKeyRetrieval` using invalid `=` syntax

- 修复 `sslMode` 和 `allowPublicKeyRetrieval` 使用无效的 `=` 语法

### Docs / 文档

- Optimized the configuration file structure and comments

- 优化了配置文件结构和注释

## v0.1.2

> This version features the most comprehensive support for AuthMe. Users using **FLP with AuthMe** are RECOMMENDED to update as soon as possible. Subsequent versions will focus on **other bug fixes and user experience improvements**.
> 
> 这是对 Authme 支持最完善的一个版本, 建议使用 **flp 搭配 Authme** 的用户尽快更新. 接下来的版本将聚焦于其他 bug 修复和使用体验优化.

### AuthMe 6.0 Auto-Integration / AuthMe 6.0 自动集成

- FLP automatically takes over AuthMe 6.0 premium verification — no manual `enablePremium=true` needed
- `forceEnablePremium()`: sets `enablePremium=true` via AuthMe's Settings API and persists to config.yml
- `unregisterPremiumPacketListener()`: unregisters AuthMe's PacketEvents listener so FLP is the sole verifier
- Lazy re-assert on each premium login to handle `/authme reload` re-registering the listener

- FLP 自动接管 AuthMe 6.0 正版验证——无需手动设置 `enablePremium=true`
- `forceEnablePremium()`：通过 AuthMe 的 Settings API 设置 `enablePremium=true` 并持久化到 config.yml
- `unregisterPremiumPacketListener()`：注销 AuthMe 的 PacketEvents 监听器，FLP 成为唯一验证源
- 每次正版登录时懒式重新断言，防止 `/authme reload` 重新注册监听器

### AuthMe 6.0 First-Time Premium Fix / AuthMe 6.0 首次正版玩家修复

- Fix preJoin dialog not skipped for first-time premium players (getAuth()==null case)
- Pre-create AuthMe DB record via saveAuth() + updatePremiumUuid() during LOGIN phase
- Fix premium session not persisted after first login (breaks reconnect)
- ForceLoginTask now saves onlinemodePreferred=true even when forceLogin returns ALREADY_AUTHENTICATED

- 修复首次正版玩家的 preJoin 对话框未跳过的问题（getAuth()==null 情况）
- 在 LOGIN 阶段通过 saveAuth() + updatePremiumUuid() 预创建 AuthMe 数据库记录
- 修复首次登录后正版会话未持久化（导致重连失败）
- ForceLoginTask 现在即使 forceLogin 返回 ALREADY_AUTHENTICATED 也会保存 onlinemodePreferred=true

### Other Bug Fixes / 其他 Bug 修复

- Fix version check showing "unknown" instead of actual version (add Implementation-Version to JAR manifest)
- Fix `/flp cracked` command showing wrong message when targeting another player
- Fix START packet forwarded after source.kick() causing vanilla server to overwrite FLP's kick message

- 修复版本检查显示 "unknown" 而非实际版本（在 JAR manifest 中添加 Implementation-Version）
- 修复 `/flp cracked` 命令对其他玩家显示错误消息
- 修复 source.kick() 后 START 包仍被转发导致 vanilla 服务器覆盖 FLP 的踢出消息

## v0.1.1

### AuthMe 6.0 preJoin Fix / AuthMe 6.0 preJoin 修复

- Fix AuthMe 6.0 preJoin dialog blocking premium players
- Fix the deadlock where the dialog blocked the connection before the premium flag could be set
- Add startup validation: ERROR log when AuthMe 6.0 preJoin is enabled but `enablePremium` is disabled

- 修复 AuthMe 6.0 preJoin 对话框阻塞正版玩家
- 修复对话框阻塞连接导致 premium 标记永远无法写入的死锁
- 新增启动验证：当 AuthMe 6.0 的 preJoin 开启但 `enablePremium` 未启用时输出 ERROR 日志

### Tab Completion / 命令补全

- Add tab completion for `/flp` command subcommands (bukkit + folia)

- 为 `/flp` 命令的子命令添加 Tab 补全（bukkit + folia）

### Language Files / 语言文件

- Fix `messages_zh.yml` was not saved to the plugin configuration directory

- 修复 `messages_zh.yml` 未保存到插件配置目录

### Build / 构建

- Add version number to JAR filenames

- JAR 文件名包含版本号

## v0.1.0

> **v0.1.0 marks the first major release of FastLoginPlus** — a consolidated upgrade covering new platforms, security hardening, and important bug fixes accumulated since the fork. **But this is NOT a stable release**.
>
> **v0.1.0 是 FastLoginPlus 的首个重要更新版本** —— 涵盖新平台支持、安全加固以及 fork 以来积累的重要修复。**但这不会是一个稳定版本**

### Folia Platform Support / Folia 平台支持

- Add Folia as a separate module with FoliaScheduler
- The stability on Folia still requires long-term observation and verification

- 新增 Folia 作为独立模块，使用 FoliaScheduler
- Folia 支持的稳定性仍需长期观察和检验

### Proxy SQLite Support / 代理端 SQLite 支持

- Add SQLite support for BungeeCord with sqlite-jdbc dependency and shading
- Add missing org.sqlite relocation in Velocity for class isolation

- 为 BungeeCord 添加 SQLite 支持，包含 sqlite-jdbc 依赖和重定位
- 为 Velocity 添加缺失的 org.sqlite 重定位以实现类隔离

### Automatic Update Check / 自动更新检查

- Add UpdateChecker for GitHub Releases with startup and periodic checks
- OP players receive update notifications on login
- Config option: check-update (default: true)

- 新增 UpdateChecker 检查 GitHub Releases，支持启动时和周期性检查
- OP 玩家登录时会收到更新通知
- 配置项：check-update（默认：true）

### FastLoginAntiBotEvent / 反机器人事件

- Add FastLoginAntiBotEvent interface in core (exposes address/username/action)
- Fire event on Block/Ignore actions, allow cancellation to bypass

- 新增 FastLoginAntiBotEvent 核心接口（暴露 address/username/action）
- 在 Block/Ignore 操作时触发事件，允许取消以绕过

### Multi-Layer Anti-Bot Upgrade / 多层反机器人升级

- PerIpRateLimiter: dual-window (burst + long) per-IP rate limiting
- IpBanManager: temporary IP ban with auto-expiration
- TrustedIpSet: immutable whitelist bypassing all anti-bot checks
- WindowCounter: thread-safe dual-window counter per IP
- AntiBotService: refactored as multi-layer orchestrator (trusted IP → ban check → per-IP limit → global limit)
- New config keys: per-ip-connections, per-ip-expire, burst-limit, burst-window, ban-duration, trusted-ips

- PerIpRateLimiter：双窗口（突发 + 长期）每 IP 速率限制
- IpBanManager：临时 IP 封禁，自动过期
- TrustedIpSet：不可变白名单，绕过所有反机器人检查
- WindowCounter：每 IP 线程安全双窗口计数器
- AntiBotService：重构为多层编排器（可信 IP → 封禁检查 → 每 IP 限制 → 全局限制）
- 新配置项：per-ip-connections、per-ip-expire、burst-limit、burst-window、ban-duration、trusted-ips

### Bug Fixes / 修复

- Fix chunk rendering race condition on first login (fixes TuxCoding/FastLogin#1358)
- Add lock to SQLiteStorage.deleteProfile for thread safety
- Unify version management — use ${project.version}

- 修复首次登录时区块渲染竞态条件（修复 TuxCoding/FastLogin#1358）
- 为 SQLiteStorage.deleteProfile 添加锁以确保线程安全
- 统一版本管理 — 使用 ${project.version}

## v0.0.7

### Command Namespace Unification / 命令命名空间统一

- Unify all commands under `/flp` namespace: `/premium` → `/flp premium`, `/cracked` → `/flp cracked`, `/fldelete` → `/flp delete`
- Remove legacy standalone command definitions from plugin.yml
- Update command references in config comments and user-facing messages

- 统一所有命令到 `/flp` 命名空间：`/premium` → `/flp premium`，`/cracked` → `/flp cracked`，`/fldelete` → `/flp delete`
- 从 plugin.yml 移除旧的独立命令定义
- 更新配置注释和用户消息中的命令引用

### PlaceholderAPI Placeholders / PlaceholderAPI 变量

- Add `%fastloginplus_is_premium%` placeholder (returns true/false)
- Add `%fastloginplus_floodgate%` placeholder (returns Java/Bedrock/Linked/Unknown)

- 新增 `%fastloginplus_is_premium%` 变量（返回 true/false）
- 新增 `%fastloginplus_floodgate%` 变量（返回 Java/Bedrock/Linked/Unknown）

### Bedrock Player Support / 基岩版玩家支持

- Upgrade Geyser 2.2.1→2.10.1 and Floodgate 2.2.3→2.2.5
- Add Bedrock player support section to README with Geyser/Floodgate guidance

- 升级 Geyser 2.2.1→2.10.1 和 Floodgate 2.2.3→2.2.5
- README 新增基岩版玩家支持章节，含 Geyser/Floodgate 指引

### Code Cleanup / 代码清理

- Remove dead version-detection and reflection code from AsyncScheduler

- 移除 AsyncScheduler 中无用的版本检测和反射代码

## v0.0.6

- Change `autoRegister`, `premiumUuid`, `nameChangeCheck` defaults to `true`
- Change `/cracked` default permission from `true` to `op`
- Optimize config comments and sections
- Clarify `offline-whitelist` only controls access, not registration
- Add `LOGIN-FLOW.md` documentation

- 将 `autoRegister`、`premiumUuid`、`nameChangeCheck` 默认值改为 `true`
- 将 `/cracked` 默认权限从 `true` 改为 `op`
- 优化配置文件注释和分区
- 明确 `offline-whitelist` 仅控制访问权限，不负责注册
- 新增 `LOGIN-FLOW.md` 登录流程文档

## v0.0.5

### AuthMe 6.0 Compatibility / AuthMe 6.0 兼容

Full compatibility with AuthMe 6.0's premium system. FLP auto-detects AuthMe version at startup and adapts without user intervention:

- **Runtime version detection** — checks for `PendingPremiumCache` class existence (more reliable than version string parsing)
- **Premium state injection** — after Mojang verification, injects `PendingPremiumCache` + `PremiumLoginVerifier` via reflection to skip AuthMe's Pre-Join dialog
- **Auto-registration** — new premium players are force-registered in AuthMe's database and marked as premium
- **Session restore** — respects AuthMe 6.0's own premium bypass instead of interfering with it
- **`/flp` command namespace** — when AuthMe 6.0 is detected, FLP registers commands under `/flp` (e.g. `/flp premium`) to avoid conflict with AuthMe's `/premium`
- **Startup logging** — detailed AuthMe compatibility info on server start (version, enablePremium status, active behavior)
- All reflection calls wrapped in try-catch — falls back to no-op if AuthMe internal classes change

完整兼容 AuthMe 6.0 的正版系统。FLP 启动时自动检测 AuthMe 版本并适配，无需用户手动配置：

- **运行时版本检测** — 通过 `PendingPremiumCache` 类存在性判断（比版本号解析更可靠）
- **正版状态注入** — Mojang 验证后通过反射注入 `PendingPremiumCache` + `PremiumLoginVerifier`，跳过 AuthMe 的 Pre-Join 对话框
- **自动注册** — 新正版玩家会被强制注册到 AuthMe 数据库并标记为 premium
- **会话恢复** — 尊重 AuthMe 6.0 自身的 premium 跳过机制，不再互相干扰
- **`/flp` 命令命名空间** — 检测到 AuthMe 6.0 时，FLP 命令注册为 `/flp`（如 `/flp premium`），避免与 AuthMe 的 `/premium` 冲突
- **启动日志** — 服务器启动时输出详细的 AuthMe 兼容信息（版本、enablePremium 状态、当前行为）
- 所有反射调用都有 try-catch 保护 — AuthMe 内部类变更时自动降级为空操作

## v0.0.4

### Session Retry / 会话验证重试

Added automatic retry for Mojang session server verification (Spigot+ProtocolLib only):

- When `hasJoined` fails due to a network error (IOException), the plugin now retries up to `mojang-retry-count` times (default 3) with `mojang-retry-delay` ms between attempts (default 1000)
- HTTP 204 (auth rejection) is NOT retried — only network errors
- New kick message `session-retry-exhausted` shown when all retries fail

新增 Mojang 会话服务器验证自动重试（仅 Spigot+ProtocolLib）：

- 当 `hasJoined` 因网络错误（IOException）失败时，插件会自动重试，最多 `mojang-retry-count` 次（默认 3），每次间隔 `mojang-retry-delay` 毫秒（默认 1000）
- HTTP 204（认证拒绝）不会重试，仅重试网络错误
- 所有重试耗尽后显示新的踢出消息 `session-retry-exhausted`

### Log Improvement / 日志优化

Improved login flow log readability:

- Replaced raw ProtocolLib packet dump with human-readable messages
- Moved internal details (packet type override, encryption setup) to DEBUG level
- Added "Verifying session for {player}" log at session check start

优化登录流程日志可读性：

- 用人类可读的消息替代原始 ProtocolLib 包名输出
- 将内部细节（包类型覆盖、加密初始化）降为 DEBUG 级别
- 新增"Verifying session for {player}"日志，标识验证开始

### Dependency / 依赖更新

- Mockito 5.17.0 → 5.18.0 (fixes JDK 25 ByteBuddy compatibility / 修复 JDK 25 ByteBuddy 兼容性)

## v0.0.3

### SkinsRestorer Compatibility / SkinsRestorer 兼容性

Fixed FastLogin overriding SkinsRestorer custom skins ([TuxCoding/FastLogin#1347](https://github.com/TuxCoding/FastLogin/issues/1347)):

- Player skins set via SkinsRestorer's `/skin` command are now preserved — FastLoginPlus skips its own skin when SR has a custom skin for the player
- Added `SkinsRestorerCompat` helper using SR's official API (`PlayerStorage.getSkinIdOfPlayer`)
- SkinsRestorer listed as `softdepend` in `plugin.yml` to ensure correct load order

修复 FastLogin 覆盖 SkinsRestorer 自定义皮肤的问题（[TuxCoding/FastLogin#1347](https://github.com/TuxCoding/FastLogin/issues/1347)）：

- 通过 SkinsRestorer `/skin` 命令设置的皮肤现在会被保留 — 当 SR 有玩家的自定义皮肤时，FastLoginPlus 会跳过自身皮肤
- 新增 `SkinsRestorerCompat` 辅助类，使用 SR 官方 API（`PlayerStorage.getSkinIdOfPlayer`）
- `plugin.yml` 中添加 SkinsRestorer 为 `softdepend`，确保加载顺序正确

### Bug Fixes / Bug 修复

- **`forwardSkin: false` not working on Paper**: The `PaperCacheListener` was always registered on Paper regardless of the `forwardSkin` config. Now respects the setting.

- **Paper 服务端 `forwardSkin: false` 无效**：`PaperCacheListener` 在 Paper 上无论 `forwardSkin` 设置如何都会注册，现已修复为正确读取配置。

## v0.0.2

### Offline Whitelist / 离线白名单

Replaced `switchMode` with a new standalone **offline-whitelist** feature:

- `offline-whitelist: true` — Only players already in the database can join as cracked/offline
- New offline (cracked) players are kicked with a localized message
- Premium players are unaffected — auto-detected via Mojang API and allowed to join
- Existing cracked players in the database continue to join normally
- The Mojang API check is automatically triggered when `offline-whitelist` is enabled (no need to enable `autoRegister` or `nameChangeCheck` separately)

用新的独立 **离线白名单** 功能替代 `switchMode`：

- `offline-whitelist: true` — 仅数据库中已有记录的玩家可以离线模式加入
- 新的离线玩家会被踢出，显示本地化消息
- 正版玩家不受影响 — 通过 Mojang API 自动检测并允许加入
- 数据库中已有的离线玩家仍可正常加入
- 开启 `offline-whitelist` 后自动触发 Mojang API 检查（无需额外开启 `autoRegister` 或 `nameChangeCheck`）

### Removed / 移除

- **`switchMode`** config option removed — replaced by `offline-whitelist`
- **`switchMode`** 配置项移除 — 由 `offline-whitelist` 替代

## v0.0.1

First independent release of FastLoginPlus, forked from [FastLogin](https://github.com/TuxCoding/FastLogin) with enhancements.

FastLoginPlus 首个独立版本，基于 [FastLogin](https://github.com/TuxCoding/FastLogin) fork 并增强。

### Project Renaming / 项目更名

- Renamed to **FastLoginPlus**, Maven artifact `fastlogin` → `fastloginplus`
- Independent versioning starting from v0.0.1
- Java package names (`com.github.games647.fastlogin`) unchanged for upstream compatibility

- 项目更名为 **FastLoginPlus**，Maven 坐标从 `fastlogin` 改为 `fastloginplus`
- 独立版本号体系，从 v0.0.1 起步
- Java 包名（`com.github.games647.fastlogin`）保留不变，便于合并上游更新

### Bug Fixes / Bug 修复

- **switchMode kicked new premium players**: When `switchMode` was enabled, premium players joining for the first time were incorrectly kicked ([#1359](https://github.com/TuxCoding/FastLogin/issues/1359)). Now premium players are properly detected via Mojang API and allowed to join. (Note: `switchMode` has since been replaced by `offline-whitelist` in v0.0.2)

- **switchMode 误踢正版新玩家**：上游 `switchMode` 开启后，首次加入的正版玩家会被错误踢出（[#1359](https://github.com/TuxCoding/FastLogin/issues/1359)）。修复后，正版玩家会通过 Mojang API 自动检测并正确放行。（注：`switchMode` 已在 v0.0.2 中被 `offline-whitelist` 替代）

### SQLite Concurrency / SQLite 并发优化

- **WAL mode** — Write-Ahead Logging for better concurrent read/write under proxy architecture
- **Busy timeout** — 5-second wait instead of instant `SQLITE_BUSY` errors
- **Thread-safe operations** — `ReentrantLock` on all `loadProfile` / `save` calls

- 启用 **WAL (Write-Ahead Logging)** 模式，代理架构下多线程读写不再互相阻塞
- 设置 **5 秒 busy timeout**，避免 `SQLITE_BUSY` 瞬间报错
- 所有 `loadProfile` / `save` 操作加 `ReentrantLock`，防止竞态条件

### fldelete Enhancement / fldelete 命令增强

The upstream `fldelete` was bare-bones (hardcoded English, no premium protection, broken under BungeeCord). Fully rewritten:

- Localized message strings (multi-language support)
- Premium player protection — cannot delete online-mode player records
- BungeeCord support via PluginMessage forwarding
- Fires `BukkitFastLoginPremiumToggleEvent` on successful deletion

上游的 `fldelete` 实现较为简陋（硬编码英文、无 premium 保护、BungeeCord 下不可用），本版本重写了完整实现：

- 消息文本改为本地化，支持多语言
- 新增 premium 玩家保护——不允许删除在线模式玩家的记录
- 支持 BungeeCord 环境，通过 PluginMessage 转发删除请求
- 删除成功后触发 `BukkitFastLoginPremiumToggleEvent` 事件

### Multi-language System / 多语言系统

- Built-in **English** (`messages_en.yml`) and **Chinese** (`messages_zh.yml`) language files
- `language` option in `config.yml` to select language (`en` / `zh` / custom)
- Custom languages supported — set any value (e.g. `ja`), plugin loads `messages_ja.yml`, falls back to English if missing
- Auto-detects missing keys on startup and fills them from the English default
- Config comments in bilingual (English + Chinese)

- 内置 **英文**（`messages_en.yml`）和 **中文**（`messages_zh.yml`）语言文件
- `config.yml` 中通过 `language` 选项指定使用的语言（`en` / `zh` / 自定义）
- 支持自定义语言文件：设置任意值（如 `ja`），插件自动加载 `messages_ja.yml`，不存在时回退到英文
- 启动时自动检测语言文件完整性，缺失的键值从英文默认文件补全
- 配置文件注释改为中英双语
