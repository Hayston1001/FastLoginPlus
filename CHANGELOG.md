# FastLoginPlus Changelog

## v0.6.0

### Bug Fixes

<details>
<summary><strong>0.5.0 Audit Fixes</strong></summary>

- **AuthMe integration**: premium-record cleanup during cracked sessions is now fail-closed —
  a premium-flagged AuthMe record without a matching FastLogin profile row (database reset or
  first login) is kept and reported, so impostors no longer get a registration window on
  premium-verified names.
- **Folia**: `VerifyResponseTask` is synced with the bukkit event-loop scheduling fix
  (upstream 327c14a3) — encryption and the fake START injection no longer race the vanilla
  handler on first login; the configure-phase listeners no longer call `Bukkit.getScheduler()`,
  which throws on Folia.
- **Anti-bot**: cleanup now shares the limiter's uptime clock — per-IP rate state is no longer
  wiped every 100 connections; lazy per-IP cleanup is throttled to once per second; expiry is
  fixed against a creation race; anti-bot config values are validated with fallback-to-default
  warnings.
- **Velocity**: the anti-bot event in `onPreLogin` is awaited with `EventTask.withContinuation`
  instead of blocking the Netty event loop; premium checks run on the plugin scheduler instead
  of the shared async event executor.
- **Concurrency**: `pendingConfirms` is now a concurrent set and the premium-warning gate uses
  an atomic check-and-add.
- **Scheduler lifecycle**: a shutdown flag stops all platform schedulers before shared resources
  are closed, so tasks no longer touch closed resources after disable; Folia's self-chaining
  relay tasks stop on disable; relay retry tasks give up after ~5 minutes.
- **Storage**: HikariCP `maxLifetime` is clamped to at least 300s with a warning (template
  default now 1800s — the old 30s caused constant connection churn); MySQL first-time inserts
  are guarded upserts that keep case-variant protection; a failed database setup closes its
  connection pool.
- **Config**: `config.yml` rewrites are atomic; template section headers are never overwritten
  by user scalars; YAML-ambiguous values stay quoted strings; the `language` value is validated;
  invalid `proxies` entries are skipped instead of crashing startup.
- **Commands & hooks**: premium/cracked/delete commands no longer touch the database on the main
  thread; Passky support is actually registered now; auth-plugin hook calls are bounded at 5s;
  replayed ENCRYPTION_BEGIN packets are rejected instead of double-verifying; the packet listener
  survives unexpected reflection errors.
- **Other**: offline-whitelist fails closed on errors; bungee login/success handlers null-guard
  missing sessions; the cracked self-command sends the correct message; the bukkit update-check
  interval now matches the documented hours semantics; kicks aimed at players that already
  disconnected (e.g. vanilla 'Took too long to log in') are skipped instead of erroring; dead
  code and locale keys removed; misleading documentation fixed; many smaller null-guards and
  race fixes.
- **Plugin message authentication (backend → proxy)**: the `ch-st`, `del-st` and `succ` channels now carry the sending backend's proxy allowlist as a trailing wire field, and the proxy drops messages whose echoed set does not contain its own proxy ID (fail-closed with a WARN) — closing the last unauthenticated plugin-message channel, which previously let any backend inside the network forge premium toggles, deletes or success-acks against the proxy database
- New `verify-backend-messages` config key (proxy template): set it to `false` only temporarily while rolling out the upgrade — upgrade backends first, the proxy last (old backends send no echo and are rejected). On single-proxy networks this is equivalent to a config-shared secret; on multi-proxy networks a compromised backend can still echo its own allowlist (documented trust-model boundary, see the code comments)
- **Storage concurrency**: a 64-stripe per-name lock now wraps every load-modify-save window (login flow, force-login persistence, proxy toggles, success-ack saves, premium/cracked commands), so concurrent flows for the same player can no longer silently overwrite each other; two fast `/flp premium|cracked` invocations also no longer double-save/double-fire
- **SQLite upsert**: first-time saves are now `INSERT ... ON CONFLICT(Name) DO UPDATE` with a byte-exact case guard mirroring MySQL's HEX protection, so two concurrent first saves of the same name collapse into one row instead of losing the profile; the row id is backfilled via a name lookup when `getGeneratedKeys()` yields nothing on the update branch; `save` failures are reported through `saveQuietly` instead of being silently swallowed. Regression tests run against a real SQLite database, including a discriminating test that fails without the lock
- **Velocity**: the `EventTask.withContinuation` continuation is now resumed exactly once on every path — a per-event CAS guard funnels all resumes through `resumeOnce`, and exceptions while applying the anti-bot decision (or firing the anti-bot event) are caught and still resume the login instead of hanging the connection until the read timeout
- **Bukkit**: the configure-phase premium path now schedules the defensive toggle relay when the carrier player is missing (mirroring the Folia branch) — a pending premium toggle no longer waits for a restart to be delivered after an empty-server window

- **AuthMe 集成**: 盗版会话路径上的 AuthMe premium 记录清理改为 fail-closed —— 当 AuthMe 记录
  带 premium 标记但 FastLogin 侧没有对应记录时(数据库重置/首次登录),记录会被保留并告警,
  冒名者无法再借注册窗口抢占已验证的正版名字. 
- **Folia**: `VerifyResponseTask` 与 bukkit 侧的事件循环调度修复同步(上游 327c14a3)—— 加密与
  伪 START 注入不再在首次登录时与原版处理器竞态; configure 阶段监听器不再调用
  `Bukkit.getScheduler()`(在 Folia 上必抛异常). 
- **反机器人**: 清理时钟与限流器统一 —— 每 100 连接不再整表清空 per-IP 限流状态; 惰性清理节流为
  每秒一次; 修复条目创建竞态; 反机器人配置值带默认回退校验与警告. 
- **Velocity**: `onPreLogin` 中的反机器人事件改用 `EventTask.withContinuation` 异步等待,
  不再阻塞 Netty 事件循环; 正版检查改在插件调度器上执行, 不再占用共享异步事件执行器. 
- **并发**: `pendingConfirms` 改为并发集合, premium 确认门禁使用原子 check-and-add. 
- **调度器生命周期**: 关闭标志在共享资源释放前停止所有平台调度器, 禁用后任务不再触碰已关闭的
  资源; Folia 自续链中继任务随插件停用而停止; 中继重试约 5 分钟后放弃. 
- **存储**: HikariCP `maxLifetime` 下限钳制为 300 秒并伴随警告(模板默认改为 1800 秒 —— 旧值
  30 秒会导致持续的连接重建); MySQL 首次插入改为带保护的 upsert, 保留大小写变体防护;
  数据库初始化失败时会关闭已建立的连接池. 
- **配置**: `config.yml` 重写改为原子操作; 模板节头不再被用户标量覆盖; YAML 歧义值保持字符串
  类型; `language` 值校验; 非法 `proxies` 条目跳过而不是使启动崩溃. 
- **命令与钩子**: premium/cracked/delete 命令不再在主线程访问数据库; Passky 支持真正注册;
  认证插件钩子调用加上 5 秒超时; 重放的 ENCRYPTION_BEGIN 包被拒绝而不是双重验证; 包监听器
  可在意外反射异常后存活. 
- **其他**: 离线白名单在出错时 fail-closed; bungee 登录/成功处理器对缺失会话判空; cracked 自助
  命令发送正确的消息; bukkit 更新检查间隔与文档的小时语义一致; 对已断开玩家的补踢改为静默跳过
  (例如原版"登录耗时过长"已将其踢出时); 清理死代码与失效语言键; 修正失实文档; 多处判空与竞态小修.
- **插件消息认证(后端 → 代理)**: `ch-st`、`del-st`、`succ` 三个通道现在携带发送方后端的代理白名单作为尾随字段, 代理端在校验回传集合不包含自身 ID 时丢弃消息并输出 WARN(fail-closed)—— 封堵最后一条无认证的插件消息通道, 此前网络内任意后端都可以伪造正版切换、删除或成功回执来篡改代理端数据库
- 新增 `verify-backend-messages` 配置键(代理端模板): 仅在滚动升级期间临时设为 `false` —— 先升级全部后端, 最后升级代理(旧后端不带回传字段会被拒绝). 单代理网络下等价于共享密钥; 多代理网络中被入侵的后端仍可回显自身白名单(信任模型边界已在代码注释与设计文档中说明)
- **存储并发**: 新增 64 条纹的名字级锁覆盖全部 load-modify-save 窗口(登录流程、强制登录持久化、代理端切换、成功回执落库、premium/cracked 命令), 同一玩家的并发流程不再互相静默覆盖; 快速连续的 `/flp premium|cracked` 也不再重复落库/重复触发事件
- **SQLite upsert**: 首次保存改为 `INSERT ... ON CONFLICT(Name) DO UPDATE`, 并带字节级大小写守卫(与 MySQL 的 HEX 保护对齐), 两个并发的首次保存会收敛为一行而不是丢失记录; upsert 走更新分支且 `getGeneratedKeys()` 无行时回退按名查询回填 rowId; 保存失败通过 `saveQuietly` 上报而不是静默吞掉. 回归测试使用真实 SQLite 数据库, 含去掉锁必失败的判别用例
- **Velocity**: `EventTask.withContinuation` 的续体现在在所有路径上恰好 resume 一次 —— 每事件一个 CAS 守卫, 所有 resume 收敛到 `resumeOnce`; 应用反机器人决策(或触发反机器人事件)抛出异常时会被捕获并仍然恢复登录, 不再把连接挂死到读超时
- **Bukkit**: configure 阶段 premium 分支在载体玩家缺失时补上防御性中继调度(与 Folia 分支对齐)—— 空服期间排队的正版切换不再要等重启才能投递

</details>

<details>
<summary><strong>Pending relay audit fixes</strong></summary>

- Fixed a race where two conflicting console toggles (`/flp premium X` then `/flp cracked X`) could relay the stale captured value: the relay task now atomically removes the queue entry and sends the CURRENT queued value (`PendingRelayStore.removeToggle`), so the last command always wins (bukkit, folia and the Paper configure-phase self-relay path).
- A pending cracked toggle for a player who joins while nobody else is online is no longer silently dropped by the Paper configure listener (autoRegister skip): the entry stays queued and is relayed to the proxy once any player reaches the PLAY phase, so the proxy database is actually flipped to cracked.
- Folia relay tasks no longer lose a toggle/delete when the carrier player quits between the async online-check and the global-region execution: the task re-checks `isOnline()` before removing the queue entry, re-chains the retry if the carrier left, and re-queues + retries when the plugin-message send itself fails.
- The proxy no longer kicks a player who is ALREADY cracked when `/flp cracked` is relayed for them (bungee + velocity): a no-state-change toggle now behaves like the already-premium skip. This also respects `kick-toggle: false` and removes the misleading "premium removed" disconnect text on a no-op toggle.
- Toggle/delete results for backend-relayed commands are now mirrored back to the backend console via a new proxy → backend plugin message (`ToggleFeedbackMessage`, channel `fb-st`, carries a locale key + the proxy UUID validated against `allowed-proxies.txt`) — the admin no longer has to check the proxy log to learn whether the queued toggle succeeded.
- Retry-task accumulation bounded: `queueToggle`/`queueDelete` now report whether a NEW entry was created, and commands only schedule a retry task for new entries — an already-queued entry keeps its live task, which picks up overwritten values at send time.
- Known limitations (documented, unchanged by design): the pending queue is per-backend (a toggle queued on backend A is delivered when A has a player online, not on other backends); entries have no TTL — admin intent is preserved until delivered or until proxy support is disabled (`clearAll`).

- 修复异值双击竞态: 控制台先执行 `/flp premium X` 再执行 `/flp cracked X` 时, 中继任务可能发送任务创建时捕获的旧值 —— 现在中继任务通过新增的 `PendingRelayStore.removeToggle` 原子地取出队列当前值再发送, 保证最后一条命令生效(bukkit、folia 及 Paper configure 阶段的自中继路径). 
- 修复 pending cracked 在目标玩家本人连入时被 Paper configure 监听器静默吞掉的问题(跳过 autoRegister 的同时不再清除队列条目): 条目保留并在任意玩家进入 PLAY 阶段后转发给代理, 代理数据库实际切换为 cracked. 
- 修复 Folia 载体玩家断连竞态: 异步在线检查与 global-region 执行之间玩家退出时, 任务会先复查 `isOnline()` 再取队列条目, 载体已离开则重新链式重试; 发送本身失败时回滚入队并重试(toggle 与 delete 一致). 
- 代理端(bungee + velocity)不再对"本来就是盗版"的目标玩家执行 kick: 无状态变化的切换与 already-premium 跳过行为对齐, 同时遵守 `kick-toggle: false` 配置, 并移除无操作时误导性的"已移除高级登录"踢出文案. 
- 后端转发的 toggle/delete 执行结果现在通过新增的 代理 → 后端 插件消息(`ToggleFeedbackMessage`, 通道 `fb-st`, 携带语言键 + 用于 `allowed-proxies.txt` 校验的代理 UUID)回显到后端控制台 —— 管理员无需再去代理日志确认排队命令的成败. 
- 重试任务堆积受限: `queueToggle`/`queueDelete` 现在返回是否新建了条目, 命令只为新条目安排重试任务 —— 已在队列中的条目沿用存活的重试任务, 其在发送时读取被覆盖后的最新值. 
- 已知限制(设计如此, 保持不变): 排队队列是按后端隔离的(在 A 后端排队的 toggle 只在 A 有玩家上线时投递); 条目无 TTL —— 管理员意图会保留直到成功投递或代理支持被关闭(`clearAll`). 

</details>

### Changes

<details>
<summary><strong>/flp help restricted to operators</strong></summary>

- The bare `/flp` command now requires server operator permission: other senders receive the localized `no-permission` message

- 裸 `/flp` 命令现在仅限服务器管理员(OP)使用: 其他发送者会收到本地化的 `no-permission` 提示

</details>

<details>
<summary><strong>Quieter update-check failures</strong></summary>

- Update-check network failures are now logged as a single WARN line with the failure reason instead of an INFO entry with a full stack trace (the trace moved to debug level)

- 更新检查的网络失败现在以一条简短的 WARN 日志记录失败原因, 不再以 INFO 级别输出完整堆栈(堆栈移至 debug 级别)

</details>

<details>
<summary><strong>ProtocolLib async design record</strong></summary>

- The decision to keep the ProtocolLib login listener registered as an async handler is now documented in `PROTOCOLLIB-ASYNC-DESIGN.md` at the repository root: rationale, compensating controls, residual risk with operator guidance for the startup self-check warning, and re-evaluation triggers
- A misleading comment in the ProtocolLib kick source was corrected (bukkit + folia)

- 保持 ProtocolLib 登录监听器以 async 方式注册的决策已记录到仓库根目录的 `PROTOCOLLIB-ASYNC-DESIGN.md`: 决策理由、补偿措施、残余风险与启动自检告警的处置指引、重新评估触发条件
- 修正 ProtocolLib 踢出源码中的一处误导性注释(bukkit + folia)

</details>

### Reminder

For users of MySQL:  
The recommended value for `lifetime` is **1800** seconds. Values below 300 are now clamped to 300 with a startup warning.

使用 MySQL 数据库的用户:  
`lifetime` 推荐值为 **1800** 秒. 模板默认值已由 30 提升为 1800,低于 300 的值 会被钳制到 300 并在启动时输出警告. 

## v0.5.0

### Pending proxy relay queue persistence

- The offline relay queue for proxy toggle/delete messages now survives restarts: messages are persisted to `pending-relay.json` (atomic rewrite), restored on startup, and corrupt files are moved aside instead of crashing the plugin. Toggles/deletes queued while nobody was online to carry them are now eventually delivered to the proxy once a player joins again.

- 代理切换/删除消息的离线中继队列现在可以跨重启存活：消息持久化到 `pending-relay.json`(原子重写)、启动时恢复, 损坏文件会被移开而不是导致插件崩溃. 此前仅存在内存中的队列(重启即丢), 现在会在玩家重新上线后最终送达代理. 

### /flp toggle null-profile guards

- `PremiumCommand`/`CrackedCommand` (bukkit+folia, self & other paths): a null `loadProfile` result (SQL exception only) now sends the localized `database-error` message instead of throwing an NPE (self paths) or reporting hardcoded English / `player-unknown` (other paths) — "not found" was misleading for a database failure
- velocity/bungee `PluginMessageListener` change branch now reads `premium-warning` via the typed `config.getBoolean(...)` instead of an unguarded `(boolean) config.get(...)` cast, which threw a `ClassCastException` on string-typed values and was silently swallowed by the async scheduler

- `PremiumCommand`/`CrackedCommand`(bukkit+folia, 自身与其他玩家路径): `loadProfile` 返回 null(仅 SQL 异常) 时改为发送本地化 `database-error` 消息, 不再抛 NPE(自身路径)或发送硬编码英文/`player-unknown`(其他玩家路径) —— 数据库故障时"记录不存在"是误导性提示
- velocity/bungee `PluginMessageListener` change 分支改用类型化 `config.getBoolean(...)` 读取 `premium-warning`, 替代无保护的 `(boolean) config.get(...)` 强转 —— 字符串型配置值会抛 `ClassCastException` 且被异步调度器静默吞掉

### /flp delete proxy fixes

- The `del-st` message now carries an `isSourceInvoker` flag: result feedback goes to the player who ran `/flp delete`, and to the proxy console when the command was issued from the console or relayed by a carrier — relay players are no longer spammed with delete results
- When no player is online to relay the delete message, the backend now queues the delete and retries every second until any player joins (aligned with the toggle-command behaviour) instead of silently dropping it; Folia uses a chained delayed task since it has no global repeating scheduler
- Proxy-side `/flp delete` now reports `database-error` when the database query itself fails (instead of lying "record not found"), fires the premium-toggle event on success, and re-checks the row when `deleteProfile` returns false to distinguish a real failure from a concurrent removal
- The standalone error path now uses the localized `database-error` message instead of hardcoded English text; reading legacy payloads (name only, no flag) degrades to "console invoker"
- Add `DeletePremiumMessageTest` covering the round trip, the console-relay flag and the legacy-format fallback

- `del-st` 消息现在携带 `isSourceInvoker` 标志: 玩家自己执行 `/flp delete` 时结果消息发给玩家本人, 控制台发起(或借中继玩家转发)时结果发给代理控制台 —— 中继玩家不再收到无关的删除结果
- 后端无玩家在线时不再静默丢弃: 删除请求入队并每秒重试直到有玩家上线(与 toggle 命令行为对齐); Folia 因无全局循环调度器改用链式延迟任务
- 代理端 `/flp delete` 在数据库查询本身失败时提示 `database-error`(不再谎报"记录不存在"), 删除成功时 fire 正版切换事件, 且 `deleteProfile` 返回 false 时复查记录以区分真失败与并发删除
- standalone 报错路径改用本地化 `database-error` 消息, 移除硬编码英文; 读取旧格式消息(仅玩家名、无标志)时降级为"控制台发起"
- 新增 `DeletePremiumMessageTest`: 覆盖往返序列化、控制台中继标志与旧格式容错

### Per-platform config templates

- BungeeCord/Velocity now generate `config.yml` from a dedicated `config-proxy.yml` template: backend-only keys (`verifyClientKeys`, `respectIpLimit`) are no longer present, and comments describe the proxy's role (decision maker: Mojang queries, database, force-login forwarding)
- Bukkit/Folia config comments now mark which keys lose effect (or only partially apply) when the server runs behind a proxy — `database`, `anti-bot`, Floodgate keys, JoinManagement keys and more
- ConfigRefresher preserves values for scalar keys written without a template value (e.g. `ServerRSAPublicKeyFile`)
- `loadFile` defaults now follow the selected config template (backend vs proxy), and `setConfigTemplate` fails fast when called after `load` instead of silently misbehaving

- BungeeCord/Velocity 现在使用专属的 `config-proxy.yml` 模板生成 `config.yml`: 后端专属键(`verifyClientKeys`、`respectIpLimit`)不再出现, 注释描述代理端职责(决策方: Mojang 查询、数据库、转发强制登录)
- Bukkit/Folia 的配置注释现在标明代理子服模式下失效(或仅部分生效)的键 — `database`、`anti-bot`、Floodgate 相关键、JoinManagement 相关键等
- ConfigRefresher 现在会保留模板中无默认值的标量键(如 `ServerRSAPublicKeyFile`)的用户值

### Proxy premium row persistence

- Proxies now persist a `premium=true` row themselves after verifying an online-mode session (`ForceLoginManagement` null hook branch) — no longer relying solely on the backend's `SuccessMessage` ack, which AuthMe 6.0 proxy deployments never send (REGISTER action skips ForceLoginTask when the AuthMe record already exists; LOGIN action returns false from `forceLogin` after `AsynchronousJoin` bypasses)
- Verified-premium sessions where the auth plugin reports failure (`forceLogin` returns false) now still ack the proxy, restoring the ack persistence path
- Fixes #5: a player with a premium row can no longer be let in offline by `secondAttemptCracked` after a session expiry
- Add `ForceLoginManagementTest` covering proxy null-branch persistence, row upgrade semantics, AuthMe 6.0 bypass ack, success-path regression and cracked-path regression

- 代理在验证正版会话后现在由自身直接写入 `premium=true` 行(`ForceLoginManagement` 的 null hook 分支), 不再单独依赖后端 `SuccessMessage` 回执 —— AuthMe 6.0 代理部署下该回执不会发送(REGISTER 动作因 AuthMe 记录已存在跳过 ForceLoginTask; LOGIN 动作 `forceLogin` 因 `AsynchronousJoin` 已认证返回 false)
- 已核实正版但认证插件报失败(`forceLogin` 返回 false)的会话现在也会向代理补发回执, 恢复回执持久化通道
- 修复 #5: 有正版记录的玩家不会再因会话过期被 `secondAttemptCracked` 放行进离线模式
- 新增 `ForceLoginManagementTest`, 覆盖代理 null 分支落库、行升级语义、AuthMe 6.0 bypass 补发回执、成功路径回归与 cracked 路径回归

### SQLite case-insensitive names

- SQLite `premium` table now creates `Name` with `COLLATE NOCASE`: Minecraft usernames are case-insensitive ("Steve" and "steve" are the same account), so a player can no longer end up with two rows (one premium, one cracked) that differ only by letter case — matching MySQL's default case-insensitive collation
- On startup, an existing case-sensitive `premium` table is migrated in one transaction (rename → recreate → copy → drop); the migration is idempotent, preserves all rows, and keeps one row per name variant when a database already contains premium + cracked rows that differ only by case (premium wins, otherwise the oldest row)
- Add `SQLiteStorageTest` covering case-insensitive lookup, case-variant duplicate rejection, legacy table migration and migration idempotency

- SQLite `premium` 表的 `Name` 列现在使用 `COLLATE NOCASE` 创建: Minecraft 玩家名不区分大小写("Steve" 与 "steve" 是同一账号), 同一玩家不会再出现仅大小写不同的两行(一行正版、一行离线) —— 与 MySQL 默认不区分大小写的 collation 对齐
- 启动时对既有的区分大小写 `premium` 表做一次性迁移(重命名 → 重建 → 复制 → 删除, 单事务), 迁移幂等且保留所有行
- 新增 `SQLiteStorageTest`, 覆盖不敏感查找、大小写变体重复行拒绝、旧表迁移与迁移幂等性

### AsyncToggleMessage NPE on database failure

- `/premium` and `/cracked` toggles (BungeeCord + Velocity) no longer throw NullPointerException when the profile lookup fails (SQLite lock timeout, MySQL down, dropped connection). The task aborts, sends the new `database-error` message to the invoker and logs the abort — previously the command silently did nothing and only a stack trace appeared on the proxy console

- BungeeCord 与 Velocity 的 `/premium`、`/cracked` 切换在数据库查询失败时(SQLite 锁超时、MySQL 宕机、连接断开)不再抛空指针异常: 任务中止, 向操作者发送新增的 `database-error` 提示并记录日志 —— 此前命令无声无息地无效, 只在代理控制台留下一行堆栈

### Other Bug Fixes

- Session verification aborts when the player disconnects mid-verification (e.g. the user cancels the login while Mojang is still answering): no more Mojang queries or ghost-kicks for players who are no longer connecting (`VerifyResponseTask` checks the Netty channel before proceeding)
- Velocity login sessions are now keyed by the connection's remote address instead of the connection object, fixing lost session state between `GameProfileRequest` (which exposes a different `InboundConnection` instance) and later events; the success-ack path no longer NPEs when the session is already gone
- Proxy toggle messages for admin operations on other players now use the existing `-other` message variants (the dedicated keys were removed)
- Bukkit's toggle relay no longer double-sends a pending toggle: `remove-if-present` guarantees at most one relay per toggle even when the configure listener or a retry already relayed it; Folia now queues and retries every second when no player is online, matching Bukkit's behaviour

- 玩家在异步会话验证期间断开(如 Mojang 应答中取消登录)时验证任务立即中止: 不再向已断开的连接查询 Mojang 或踢出幽灵玩家(`VerifyResponseTask` 在继续前检查 Netty channel 活跃状态)
- Velocity 登录会话改为按连接远端地址(remote address)而非连接对象存储: 修复 `GameProfileRequest`(暴露不同的 `InboundConnection` 实例)与后续事件之间会话状态丢失; 会话已消失时 success 回执路径不再抛 NPE
- 代理端对"操作其他玩家"的管理切换消息改用已有的 `-other` 消息变体(移除了不再使用的专用键)
- Bukkit 切换中继不再重复发送待处理切换: `remove-if-present` 保证每个切换至多中继一次(即使 configure 监听器或重试已先中继); Folia 在无玩家在线时现在同样入队并每秒重试, 与 Bukkit 行为对齐

### Logging & Diagnostics

- Added proxy-side debug logs for the premium toggle flow and `/flp` command diagnostics (registration result, invocation)

- 新增代理端正版切换流程的 debug 日志与 `/flp` 命令诊断日志(注册结果、命令调用)

### Documentation

- README updates plus config-comment documentation(EFFECT-line)

- README 更新及配置注释文档化(EFFECT-line)

## v0.4.0

### Paper Configure Phase autoRegister

- When uses proxy, Paper backend now auto-registers players during the configuration phase via `AsyncPlayerConnectionConfigureEvent`, running before AuthMe's listener — no more `HorriblePlayerLoginEventHack`
- UUID mismatch guard prevents cracked players from being auto-registered as premium
- Startup log confirms configure listener registration for diagnostics

- 代理场景下, Paper 后端现在通过 `AsyncPlayerConnectionConfigureEvent` 在配置阶段自动注册玩家, 早于 AuthMe 的监听器运行 — 而不使用 `HorriblePlayerLoginEventHack`
- UUID 不匹配守卫防止离线玩家被误注册为正版
- 启动日志确认配置监听器注册成功, 便于诊断

### Pending Toggle Self-Relay

- PLAY-phase self-relay replaces old retry mechanism — when a player toggles premium/cracked mode, the toggle reliably applies on next join
- Respects kick-toggle config, bypasses UUID guard for pending toggles, handles offline toggle when no relay player
- Proxy-side kick on toggle: correctly kicks the target player and resolves premium UUID
- Applied to Folia module as well

- PLAY 阶段自中继取代旧的延迟重试机制 — 玩家切换正版/离线模式后, 切换在下一次进入时可靠生效
- 遵循 kick-toggle 配置, 绕过 UUID 守卫处理待处理的切换, 处理无中继玩家时的离线切换
- 代理端切换时正确踢出目标玩家并解析正版 UUID
- 同样应用于 Folia 模块

### AuthMe Cleanup on Cracked Switch

- When a player switches from premium to cracked, FLP now thoroughly cleans AuthMe records: clears premium flag, force-unregisters accounts (supports AuthMe 5.x and 6.0), purges in-memory caches
- Second-chance cleanup via `ensureNotPremium()` on cracked login

- 正版玩家切换到离线时, FLP 彻底清理 AuthMe 记录：清除 premium 标记, 强制注销账号(支持 AuthMe 5.x 和 6.0), 清除内存缓存
- 离线登录时通过 `ensureNotPremium()` 二次兜底清理

### Bug Fixes

- Remove duplicate `[FLP]` prefix from log messages
- Hide player name tab completion when missing `.other` permission
- Enforce command permissions for `/flp` subcommands
- Fix `PremiumCommand` kick message using wrong language key (`remove-premium` → `add-premium`)
- Fix premium-warning message only for self-command, close preJoin login dialog on cracked→premium switch
- Fix SQLite native library loading on Velocity/BungeeCord by removing `org.sqlite` relocation
- Fix `CrackedCommand` NPE in proxy mode — defer `getStorage()` until after forward check
- `mojang-retry-count` and `mojang-retry-delay` now apply to ALL Mojang API calls (both proxy-side Name→UUID lookup and backend session verification), with exponential backoff

- 移除日志消息中重复的 `[FLP]` 前缀
- 缺少 `.other` 权限时隐藏玩家名 tab 补全
- 强制执行 `/flp` 子命令的权限检查
- 修复 `PremiumCommand` 踢出消息使用了错误的语言键(`remove-premium` → `add-premium`)
- 修复 premium-warning 消息仅对本人命令显示, cracked→premium 切换时关闭 preJoin 登录对话框
- 移除 `org.sqlite` 重定位, 修复 Velocity/BungeeCord 上 SQLite 原生库加载失败
- 修复代理模式下 `CrackedCommand` NPE — 将 `getStorage()` 延迟到转发检查之后
- `mojang-retry-count` 和 `mojang-retry-delay` 现在适用于所有 Mojang API 调用(代理端 Name→UUID 查询和后端会话验证), 带指数退避

### Documentation

- Add more info about Velocity and BungeeCord to README
- Add platform labels to issue template
- Explain AuthMe cleanup behavior when switching to cracked mode

- README 新增更多关于 Velocity 和 BungeeCord 的说明消息
- Issue 模板新增平台标签
- 说明切换到离线模式时 AuthMe 清理行为

## v0.3.1 (Hotfix)

### Bug Fix(Critical)

- Fix `ClassCastException: Integer cannot be cast to Long` on login — SnakeYAML parses small numeric config values (e.g. `mojang-retry-delay: 500`) as `Integer`, but the code directly cast to `(long)` which crashes. Use `Number.longValue()`/`intValue()` for safe conversion across all numeric config reads

- 修复登录时 `ClassCastException: Integer cannot be cast to Long` — SnakeYAML 将较小的数值配置(如 `mojang-retry-delay: 500`)解析为 `Integer`, 但代码直接强转 `(long)` 导致崩溃. 所有数值配置读取改用 `Number.longValue()`/`intValue()` 安全转换

## v0.3.0

### Bug Fixes(Major)

- Anti-bot module audit — 6 bug fixes: clock jump back no longer throws in TickingRateLimiter, batch expire stale records, compareTo uses correct expireTime, global rate limit checked before per-IP, periodic cleanup every 100 connections, sanitize usernames in log messages
- Fix `forwardSkin: false` not working on Paper — PaperCacheListener now checks config before setting skin
- Fix SkinsRestorer skin overwritten by Paper filledProfileCache — set empty placeholder textures to prevent `complete(true)` pulling stale skin
- Guard against null `floodgate_data_handler` in ProtocolLib pipeline — prevent NPE if Floodgate renames/removes the handler

- 反机器人模块审计 — 6 个 bug 修复：TickingRateLimiter 时钟回退不再抛异常, 批量过期陈旧记录, compareTo 使用正确的 expireTime, 全局限制在每 IP 限制之前检查, 每 100 连接定期清理, 日志中用户名消毒
- 修复 Paper 上 `forwardSkin: false` 无效 — PaperCacheListener 现在在设置皮肤前检查配置
- 修复 SkinsRestorer 皮肤被 Paper filledProfileCache 覆盖 — 设置空占位纹理防止 `complete(true)` 拉取旧皮肤
- 防止 ProtocolLib pipeline 中 `floodgate_data_handler` 为 null — 避免 Floodgate 重命名/移除 handler 时 NPE

### Code Cleanup

- Remove 22 redundant default values from config `getXxx()` calls across all modules (ConfigRefresher guarantees keys exist)

- 移除 22 处 config `getXxx()` 调用中的冗余默认值(ConfigRefresher 保证键已存在)

### Session Retry Improvement

- Exponential backoff for Mojang session retry

- Mojang 会话重试改为指数退避

### Build

- Centralize 19 shared dependency versions into parent pom.xml properties
- Add FloodgateServiceTest (17 tests) and FloodgateManagementTest (15 tests)
- Upgrade maven-compiler-plugin 3.13.0 → 3.15.0

- 将 19 个共享依赖版本集中到父 pom.xml 属性
- 新增 FloodgateServiceTest(17 个测试)和 FloodgateManagementTest(15 个测试)
- maven-compiler-plugin 3.13.0 → 3.15.0

## v0.2.1

### Version Format Unification

- All plugin descriptors (plugin.yml, bungee.yml, velocity-plugin.json) now use `${revision}-${git.commit.id.abbrev}` format consistently
- Removed redundant `ManifestResourceTransformer` from bungee/velocity shade config
- Removed `META-INF/MANIFEST.MF` exclusion from shade filters that was causing MANIFEST loss

- 所有插件描述文件(plugin.yml, bungee.yml, velocity-plugin.json)现在统一使用 `${revision}-${git.commit.id.abbrev}` 格式
- 移除了 bungee/velocity shade 配置中多余的 `ManifestResourceTransformer`
- 移除了 shade filter 中导致 MANIFEST 丢失的 `META-INF/MANIFEST.MF` 排除规则

## v0.2.0

### Config Refresher

- Restore comments and keys in config.yml from the template each startup, keeping the config file aligned with the latest version and more portable

- 在每次启动时从模板恢复 config.yml 的注释和键,使得配置文件与最新版本一致,也更具可迁移性

### Log Output Optimization

- Add `debug: false` option to config.yml to reduce log verbosity

- 在 config.yml 中添加 `debug: false` 选项以减少日志冗余

### Git Hash in JAR Names

- Include git commit hash in JAR file names and MANIFEST to make file versions clearer

- 在 JAR 文件名和 MANIFEST 中包含 git 提交哈希使得文件版本更明确

### Bug Fixes

- Fix `sslMode` and `allowPublicKeyRetrieval` using invalid `=` syntax

- 修复 `sslMode` 和 `allowPublicKeyRetrieval` 使用无效的 `=` 语法

### Docs

- Optimized the configuration file structure and comments

- 优化了配置文件结构和注释

## v0.1.2

> This version features the most comprehensive support for AuthMe. Users using **FLP with AuthMe** are RECOMMENDED to update as soon as possible. Subsequent versions will focus on **other bug fixes and user experience improvements**.
> 
> 这是对 Authme 支持最完善的一个版本, 建议使用 **flp 搭配 Authme** 的用户尽快更新. 接下来的版本将聚焦于其他 bug 修复和使用体验优化.

### AuthMe 6.0 Auto-Integration

- FLP automatically takes over AuthMe 6.0 premium verification — no manual `enablePremium=true` needed
- `forceEnablePremium()`: sets `enablePremium=true` via AuthMe's Settings API and persists to config.yml
- `unregisterPremiumPacketListener()`: unregisters AuthMe's PacketEvents listener so FLP is the sole verifier
- Lazy re-assert on each premium login to handle `/authme reload` re-registering the listener

- FLP 自动接管 AuthMe 6.0 正版验证——无需手动设置 `enablePremium=true`
- `forceEnablePremium()`：通过 AuthMe 的 Settings API 设置 `enablePremium=true` 并持久化到 config.yml
- `unregisterPremiumPacketListener()`：注销 AuthMe 的 PacketEvents 监听器, FLP 成为唯一验证源
- 每次正版登录时懒式重新断言, 防止 `/authme reload` 重新注册监听器

### AuthMe 6.0 First-Time Premium Fix

- Fix preJoin dialog not skipped for first-time premium players (getAuth()==null case)
- Pre-create AuthMe DB record via saveAuth() + updatePremiumUuid() during LOGIN phase
- Fix premium session not persisted after first login (breaks reconnect)
- ForceLoginTask now saves onlinemodePreferred=true even when forceLogin returns ALREADY_AUTHENTICATED

- 修复首次正版玩家的 preJoin 对话框未跳过的问题(getAuth()==null 情况)
- 在 LOGIN 阶段通过 saveAuth() + updatePremiumUuid() 预创建 AuthMe 数据库记录
- 修复首次登录后正版会话未持久化(导致重连失败)
- ForceLoginTask 现在即使 forceLogin 返回 ALREADY_AUTHENTICATED 也会保存 onlinemodePreferred=true

### Other Bug Fixes

- Fix version check showing "unknown" instead of actual version (add Implementation-Version to JAR manifest)
- Fix `/flp cracked` command showing wrong message when targeting another player
- Fix START packet forwarded after source.kick() causing vanilla server to overwrite FLP's kick message

- 修复版本检查显示 "unknown" 而非实际版本(在 JAR manifest 中添加 Implementation-Version)
- 修复 `/flp cracked` 命令对其他玩家显示错误消息
- 修复 source.kick() 后 START 包仍被转发导致 vanilla 服务器覆盖 FLP 的踢出消息

## v0.1.1

### AuthMe 6.0 preJoin Fix

- Fix AuthMe 6.0 preJoin dialog blocking premium players
- Fix the deadlock where the dialog blocked the connection before the premium flag could be set
- Add startup validation: ERROR log when AuthMe 6.0 preJoin is enabled but `enablePremium` is disabled

- 修复 AuthMe 6.0 preJoin 对话框阻塞正版玩家
- 修复对话框阻塞连接导致 premium 标记永远无法写入的死锁
- 新增启动验证：当 AuthMe 6.0 的 preJoin 开启但 `enablePremium` 未启用时输出 ERROR 日志

### Tab Completion

- Add tab completion for `/flp` command subcommands (bukkit + folia)

- 为 `/flp` 命令的子命令添加 Tab 补全(bukkit + folia)

### Language Files

- Fix `messages_zh.yml` was not saved to the plugin configuration directory

- 修复 `messages_zh.yml` 未保存到插件配置目录

### Build

- Add version number to JAR filenames

- JAR 文件名包含版本号

## v0.1.0

> **v0.1.0 marks the first major release of FastLoginPlus** — a consolidated upgrade covering new platforms, security hardening, and important bug fixes accumulated since the fork. **But this is NOT a stable release**.
>
> **v0.1.0 是 FastLoginPlus 的首个重要更新版本** —— 涵盖新平台支持, 安全加固以及 fork 以来积累的重要修复. **但这不会是一个稳定版本**

### Folia Platform Support

- Add Folia as a separate module with FoliaScheduler
- The stability on Folia still requires long-term observation and verification

- 新增 Folia 作为独立模块, 使用 FoliaScheduler
- Folia 支持的稳定性仍需长期观察和检验

### Proxy SQLite Support

- Add SQLite support for BungeeCord with sqlite-jdbc dependency and shading
- Add missing org.sqlite relocation in Velocity for class isolation

- 为 BungeeCord 添加 SQLite 支持, 包含 sqlite-jdbc 依赖和重定位
- 为 Velocity 添加缺失的 org.sqlite 重定位以实现类隔离

### Automatic Update Check

- Add UpdateChecker for GitHub Releases with startup and periodic checks
- OP players receive update notifications on login
- Config option: check-update (default: true)

- 新增 UpdateChecker 检查 GitHub Releases, 支持启动时和周期性检查
- OP 玩家登录时会收到更新通知
- 配置项：check-update(默认：true)

### FastLoginAntiBotEvent

- Add FastLoginAntiBotEvent interface in core (exposes address/username/action)
- Fire event on Block/Ignore actions, allow cancellation to bypass

- 新增 FastLoginAntiBotEvent 核心接口(暴露 address/username/action)
- 在 Block/Ignore 操作时触发事件, 允许取消以绕过

### Multi-Layer Anti-Bot Upgrade

- PerIpRateLimiter: dual-window (burst + long) per-IP rate limiting
- IpBanManager: temporary IP ban with auto-expiration
- TrustedIpSet: immutable whitelist bypassing all anti-bot checks
- WindowCounter: thread-safe dual-window counter per IP
- AntiBotService: refactored as multi-layer orchestrator (trusted IP → ban check → per-IP limit → global limit)
- New config keys: per-ip-connections, per-ip-expire, burst-limit, burst-window, ban-duration, trusted-ips

- PerIpRateLimiter：双窗口(突发 + 长期)每 IP 速率限制
- IpBanManager：临时 IP 封禁, 自动过期
- TrustedIpSet：不可变白名单, 绕过所有反机器人检查
- WindowCounter：每 IP 线程安全双窗口计数器
- AntiBotService：重构为多层编排器(可信 IP → 封禁检查 → 每 IP 限制 → 全局限制)
- 新配置项：per-ip-connections, per-ip-expire, burst-limit, burst-window, ban-duration, trusted-ips

### Bug Fixes

- Fix chunk rendering race condition on first login (fixes TuxCoding/FastLogin#1358)
- Add lock to SQLiteStorage.deleteProfile for thread safety
- Unify version management — use ${project.version}

- 修复首次登录时区块渲染竞态条件(修复 TuxCoding/FastLogin#1358)
- 为 SQLiteStorage.deleteProfile 添加锁以确保线程安全
- 统一版本管理 — 使用 ${project.version}

## v0.0.7

### Command Namespace Unification

- Unify all commands under `/flp` namespace: `/premium` → `/flp premium`, `/cracked` → `/flp cracked`, `/fldelete` → `/flp delete`
- Remove legacy standalone command definitions from plugin.yml
- Update command references in config comments and user-facing messages

- 统一所有命令到 `/flp` 命名空间：`/premium` → `/flp premium`, `/cracked` → `/flp cracked`, `/fldelete` → `/flp delete`
- 从 plugin.yml 移除旧的独立命令定义
- 更新配置注释和用户消息中的命令引用

### PlaceholderAPI Placeholders

- Add `%fastloginplus_is_premium%` placeholder (returns true/false)
- Add `%fastloginplus_floodgate%` placeholder (returns Java/Bedrock/Linked/Unknown)

- 新增 `%fastloginplus_is_premium%` 变量(返回 true/false)
- 新增 `%fastloginplus_floodgate%` 变量(返回 Java/Bedrock/Linked/Unknown)

### Bedrock Player Support

- Upgrade Geyser 2.2.1→2.10.1 and Floodgate 2.2.3→2.2.5
- Add Bedrock player support section to README with Geyser/Floodgate guidance

- 升级 Geyser 2.2.1→2.10.1 和 Floodgate 2.2.3→2.2.5
- README 新增基岩版玩家支持章节, 含 Geyser/Floodgate 指引

### Code Cleanup

- Remove dead version-detection and reflection code from AsyncScheduler

- 移除 AsyncScheduler 中无用的版本检测和反射代码

## v0.0.6

- Change `autoRegister`, `premiumUuid`, `nameChangeCheck` defaults to `true`
- Change `/cracked` default permission from `true` to `op`
- Optimize config comments and sections
- Clarify `offline-whitelist` only controls access, not registration
- Add `LOGIN-FLOW.md` documentation

- 将 `autoRegister`, `premiumUuid`, `nameChangeCheck` 默认值改为 `true`
- 将 `/cracked` 默认权限从 `true` 改为 `op`
- 优化配置文件注释和分区
- 明确 `offline-whitelist` 仅控制访问权限, 不负责注册
- 新增 `LOGIN-FLOW.md` 登录流程文档

## v0.0.5

### AuthMe 6.0 Compatibility

Full compatibility with AuthMe 6.0's premium system. FLP auto-detects AuthMe version at startup and adapts without user intervention:

- **Runtime version detection** — checks for `PendingPremiumCache` class existence (more reliable than version string parsing)
- **Premium state injection** — after Mojang verification, injects `PendingPremiumCache` + `PremiumLoginVerifier` via reflection to skip AuthMe's Pre-Join dialog
- **Auto-registration** — new premium players are force-registered in AuthMe's database and marked as premium
- **Session restore** — respects AuthMe 6.0's own premium bypass instead of interfering with it
- **`/flp` command namespace** — when AuthMe 6.0 is detected, FLP registers commands under `/flp` (e.g. `/flp premium`) to avoid conflict with AuthMe's `/premium`
- **Startup logging** — detailed AuthMe compatibility info on server start (version, enablePremium status, active behavior)
- All reflection calls wrapped in try-catch — falls back to no-op if AuthMe internal classes change

完整兼容 AuthMe 6.0 的正版系统. FLP 启动时自动检测 AuthMe 版本并适配, 无需用户手动配置：

- **运行时版本检测** — 通过 `PendingPremiumCache` 类存在性判断(比版本号解析更可靠)
- **正版状态注入** — Mojang 验证后通过反射注入 `PendingPremiumCache` + `PremiumLoginVerifier`, 跳过 AuthMe 的 Pre-Join 对话框
- **自动注册** — 新正版玩家会被强制注册到 AuthMe 数据库并标记为 premium
- **会话恢复** — 尊重 AuthMe 6.0 自身的 premium 跳过机制, 不再互相干扰
- **`/flp` 命令命名空间** — 检测到 AuthMe 6.0 时, FLP 命令注册为 `/flp`(如 `/flp premium`), 避免与 AuthMe 的 `/premium` 冲突
- **启动日志** — 服务器启动时输出详细的 AuthMe 兼容信息(版本, enablePremium 状态, 当前行为)
- 所有反射调用都有 try-catch 保护 — AuthMe 内部类变更时自动降级为空操作

## v0.0.4

### Session Retry

Added automatic retry for Mojang session server verification (Spigot+ProtocolLib only):

- When `hasJoined` fails due to a network error (IOException), the plugin now retries up to `mojang-retry-count` times (default 3) with `mojang-retry-delay` ms between attempts (default 1000)
- HTTP 204 (auth rejection) is NOT retried — only network errors
- New kick message `session-retry-exhausted` shown when all retries fail

新增 Mojang 会话服务器验证自动重试(仅 Spigot+ProtocolLib)：

- 当 `hasJoined` 因网络错误(IOException)失败时, 插件会自动重试, 最多 `mojang-retry-count` 次(默认 3), 每次间隔 `mojang-retry-delay` 毫秒(默认 1000)
- HTTP 204(认证拒绝)不会重试, 仅重试网络错误
- 所有重试耗尽后显示新的踢出消息 `session-retry-exhausted`

### Log Improvement

Improved login flow log readability:

- Replaced raw ProtocolLib packet dump with human-readable messages
- Moved internal details (packet type override, encryption setup) to DEBUG level
- Added "Verifying session for {player}" log at session check start

优化登录流程日志可读性：

- 用人类可读的消息替代原始 ProtocolLib 包名输出
- 将内部细节(包类型覆盖, 加密初始化)降为 DEBUG 级别
- 新增"Verifying session for {player}"日志, 标识验证开始

### Dependency

- Mockito 5.17.0 → 5.18.0 (fixes JDK 25 ByteBuddy compatibility / 修复 JDK 25 ByteBuddy 兼容性)

## v0.0.3

### SkinsRestorer Compatibility

Fixed FastLogin overriding SkinsRestorer custom skins ([TuxCoding/FastLogin#1347](https://github.com/TuxCoding/FastLogin/issues/1347)):

- Player skins set via SkinsRestorer's `/skin` command are now preserved — FastLoginPlus skips its own skin when SR has a custom skin for the player
- Added `SkinsRestorerCompat` helper using SR's official API (`PlayerStorage.getSkinIdOfPlayer`)
- SkinsRestorer listed as `softdepend` in `plugin.yml` to ensure correct load order

修复 FastLogin 覆盖 SkinsRestorer 自定义皮肤的问题([TuxCoding/FastLogin#1347](https://github.com/TuxCoding/FastLogin/issues/1347))：

- 通过 SkinsRestorer `/skin` 命令设置的皮肤现在会被保留 — 当 SR 有玩家的自定义皮肤时, FastLoginPlus 会跳过自身皮肤
- 新增 `SkinsRestorerCompat` 辅助类, 使用 SR 官方 API(`PlayerStorage.getSkinIdOfPlayer`)
- `plugin.yml` 中添加 SkinsRestorer 为 `softdepend`, 确保加载顺序正确

### Bug Fixes

- **`forwardSkin: false` not working on Paper**: The `PaperCacheListener` was always registered on Paper regardless of the `forwardSkin` config. Now respects the setting.

- **Paper 服务端 `forwardSkin: false` 无效**：`PaperCacheListener` 在 Paper 上无论 `forwardSkin` 设置如何都会注册, 现已修复为正确读取配置. 

## v0.0.2

### Offline Whitelist

Replaced `switchMode` with a new standalone **offline-whitelist** feature:

- `offline-whitelist: true` — Only players already in the database can join as cracked/offline
- New offline (cracked) players are kicked with a localized message
- Premium players are unaffected — auto-detected via Mojang API and allowed to join
- Existing cracked players in the database continue to join normally
- The Mojang API check is automatically triggered when `offline-whitelist` is enabled (no need to enable `autoRegister` or `nameChangeCheck` separately)

用新的独立 **离线白名单** 功能替代 `switchMode`：

- `offline-whitelist: true` — 仅数据库中已有记录的玩家可以离线模式加入
- 新的离线玩家会被踢出, 显示本地化消息
- 正版玩家不受影响 — 通过 Mojang API 自动检测并允许加入
- 数据库中已有的离线玩家仍可正常加入
- 开启 `offline-whitelist` 后自动触发 Mojang API 检查(无需额外开启 `autoRegister` 或 `nameChangeCheck`)

### Removed

- **`switchMode`** config option removed — replaced by `offline-whitelist`
- **`switchMode`** 配置项移除 — 由 `offline-whitelist` 替代

## v0.0.1

First independent release of FastLoginPlus, forked from [FastLogin](https://github.com/TuxCoding/FastLogin) with enhancements.

FastLoginPlus 首个独立版本, 基于 [FastLogin](https://github.com/TuxCoding/FastLogin) fork 并增强. 

### Project Renaming

- Renamed to **FastLoginPlus**, Maven artifact `fastlogin` → `fastloginplus`
- Independent versioning starting from v0.0.1
- Java package names (`com.github.games647.fastlogin`) unchanged for upstream compatibility

- 项目更名为 **FastLoginPlus**, Maven 坐标从 `fastlogin` 改为 `fastloginplus`
- 独立版本号体系, 从 v0.0.1 起步
- Java 包名(`com.github.games647.fastlogin`)保留不变, 便于合并上游更新

### Bug Fixes

- **switchMode kicked new premium players**: When `switchMode` was enabled, premium players joining for the first time were incorrectly kicked ([#1359](https://github.com/TuxCoding/FastLogin/issues/1359)). Now premium players are properly detected via Mojang API and allowed to join. (Note: `switchMode` has since been replaced by `offline-whitelist` in v0.0.2)

- **switchMode 误踢正版新玩家**：上游 `switchMode` 开启后, 首次加入的正版玩家会被错误踢出([#1359](https://github.com/TuxCoding/FastLogin/issues/1359)). 修复后, 正版玩家会通过 Mojang API 自动检测并正确放行. (注：`switchMode` 已在 v0.0.2 中被 `offline-whitelist` 替代)

### SQLite Concurrency

- **WAL mode** — Write-Ahead Logging for better concurrent read/write under proxy architecture
- **Busy timeout** — 5-second wait instead of instant `SQLITE_BUSY` errors
- **Thread-safe operations** — `ReentrantLock` on all `loadProfile` / `save` calls

- 启用 **WAL (Write-Ahead Logging)** 模式, 代理架构下多线程读写不再互相阻塞
- 设置 **5 秒 busy timeout**, 避免 `SQLITE_BUSY` 瞬间报错
- 所有 `loadProfile` / `save` 操作加 `ReentrantLock`, 防止竞态条件

### fldelete Enhancement

The upstream `fldelete` was bare-bones (hardcoded English, no premium protection, broken under BungeeCord). Fully rewritten:

- Localized message strings (multi-language support)
- Premium player protection — cannot delete online-mode player records
- BungeeCord support via PluginMessage forwarding
- Fires `BukkitFastLoginPremiumToggleEvent` on successful deletion

上游的 `fldelete` 实现较为简陋(硬编码英文, 无 premium 保护, BungeeCord 下不可用), 本版本重写了完整实现：

- 消息文本改为本地化, 支持多语言
- 新增 premium 玩家保护——不允许删除在线模式玩家的记录
- 支持 BungeeCord 环境, 通过 PluginMessage 转发删除请求
- 删除成功后触发 `BukkitFastLoginPremiumToggleEvent` 事件

### Multi-language System

- Built-in **English** (`messages_en.yml`) and **Chinese** (`messages_zh.yml`) language files
- `language` option in `config.yml` to select language (`en` / `zh` / custom)
- Custom languages supported — set any value (e.g. `ja`), plugin loads `messages_ja.yml`, falls back to English if missing
- Auto-detects missing keys on startup and fills them from the English default
- Config comments in bilingual (English + Chinese)

- 内置 **英文**(`messages_en.yml`)和 **中文**(`messages_zh.yml`)语言文件
- `config.yml` 中通过 `language` 选项指定使用的语言(`en` / `zh` / 自定义)
- 支持自定义语言文件：设置任意值(如 `ja`), 插件自动加载 `messages_ja.yml`, 不存在时回退到英文
- 启动时自动检测语言文件完整性, 缺失的键值从英文默认文件补全
- 配置文件注释改为中英双语
