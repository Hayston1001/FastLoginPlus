# FastLoginPlus Changelog

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
