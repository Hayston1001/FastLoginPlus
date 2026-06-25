# FastLoginPlus Changelog

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

- **switchMode kicked new premium players**: When `switchMode` was enabled, premium players joining for the first time were incorrectly kicked ([#1359](https://github.com/TuxCoding/FastLogin/issues/1359)). Now premium players are properly detected via Mojang API and allowed to join.

- **switchMode 误踢正版新玩家**：上游 `switchMode` 开启后，首次加入的正版玩家会被错误踢出（[#1359](https://github.com/TuxCoding/FastLogin/issues/1359)）。修复后，正版玩家会通过 Mojang API 自动检测并正确放行。

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
