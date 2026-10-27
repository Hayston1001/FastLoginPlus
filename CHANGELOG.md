# FastLoginPlus Changelog

English | [简体中文](CHANGELOG_zh.md)

## v1.0.0

![fabric](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/fabric_vector.svg)

> FLP's first stable release:
> - Build system migrated to Gradle
> - Login and identity decision chain redesigned
> - Fabric 26.2 and Velocity/EasyAuth integration

### Fabric 26.2 and EasyAuth

- **New Fabric 26.2 backend**: the Velocity backend can be used with Fabric 26.2 and EasyAuth 3.4

See the corresponding section of the [FLP wiki](https://hayston.gitbook.io/flp-wiki/) for more information.

### Proxy and backend identity

- **Backends follow proxy facts**: the login method is determined by the signed identity and policy facts declared by the proxy; the backend's local `autoLogin`, `autoRegister` or Bedrock policy no longer overrides these declarations
- **Reject when facts are missing**: missing, conflicting, expired or unreadable identity data keeps authentication locked and is no longer filled in by a default allow
- **Legacy forwarding requires signature verification**: older proxy declarations must carry a valid signature before being accepted for premium admission
- **Unverified connections are distinguished**: connections that have not completed verification are handled separately from verified premium connections
- **Waiting hint**: while identity facts are being exchanged, players see a waiting hint instead of an unresponsive login
- **No duplicate automatic login by the authentication plugin**: while FLP manages login decisions, the authentication plugin's own proxy auto-login stays disabled
- **Final result is relayed**: the proxy applies the final handshake result, and admin commands report the final result instead of only reporting that the request was submitted

### Accounts and UUIDs

- **Observations and consent are separate**: a name declaration or failed attempt no longer directly grants premium access; only when the verification service explicitly returns a failure can the same-name offline fallback be triggered
- **Verified owners can still register**: when `autoLogin` changes from `true` to `false`, a verified premium account is allowed to create a password account
- **The offline UUID can be kept**: `premiumUuid: false` keeps the name-derived UUID while the verified Mojang UUID is still used to identify account ownership
- **Corrupt premium UUID records are preserved**: corrupt records are reported and no longer discarded or silently overwritten
- **Accounts without a password need explicit release**: accounts without a password are opened for registration only after a second confirmation; ownership mismatches are refused
- **Impostor attempts do not demote a consented account**: a name that has confirmed a binding is not switched back to password login just because an impostor connects

### Bedrock and Floodgate

- **AuthMe backend handles Bedrock login earlier**: supported AuthMe 6 combinations can complete Bedrock login at an earlier stage, reducing dialog stalls
- **Bedrock facts are signed**: Bedrock pre-login facts are signed by the proxy and verified by the backend
- **Spigot proxy backend supports Bedrock pre-login**: when the authentication plugin provides the required hooks, the Spigot backend can also handle Bedrock pre-login

### Commands

- **`/flp delete <player>`**: deletes a non-premium FLP list record without deleting the authentication plugin account
- **Final feedback**: list changes sent through a proxy report the final result; a request still queued on a backend is not a completed change
- **Fabric commands**: Fabric provides the same list operations through vanilla gamemaster permissions

### Configuration and messages

- **Language files gain a version key**: `_messages-version` lets FLP add missing keys, refresh old defaults and keep custom values
- **Configuration templates match actual behavior**: the Bukkit/Folia and BungeeCord/Velocity templates state which side reads each key; a proxy backend no longer implies that a local setting takes effect
- **New `require-declared-proxy-policy`**: optional strict mode rejects legacy force messages from proxies that do not declare policy facts
- **Anti-bot always blocks**: a limit hit directly rejects the connection; `anti-bot.action` and the `Ignore` action were removed

### Other fixes

- **Paper configure phase**: standalone Paper servers keep the configure-phase login path and no longer lose premium pre-login handling
- **Encrypted sessions**: an encrypted session failure is reported as a failure and is no longer mistaken for a missing premium identity
- **AuthMe pre-login**: pre-login registration cleanup is verified before the account is treated as ready
- **Debug logging**: diagnostic messages use the plugin's own debug switch and no longer require changing the logging framework

### Upgrade notes

- **Upgrade proxy and backends together**: the new identity facts and legacy declaration signature verification require matching versions on both sides; do not keep running an old proxy behind a new backend unless you accept the documented rejection behavior
- **Removed anti-bot option**: delete `anti-bot.action` from existing configurations
- **Message files are migrated automatically**: keep custom message files, let startup migration add new keys, and review any reported extra keys
- **`require-declared-proxy-policy` defaults to off**: enable it only after all proxies have been upgraded

## v0.7.0

> This release focuses on compatibility:  
> *Enhanced support for AuthMeReloaded 6.0.0 and 6.0.1*;  
> *Adapted for newer versions of BungeeCord*;  
> *Optimized support for custom skins from SkinsRestorer*

### AuthMeReloaded 6.x

- **Significantly reduced cases where players get stuck in dialogues on first login**: Players without AuthMeReloaded records will be registered during the login phase
- **Premium login now writes the correct UUID**: Logs will be printed if writing fails, instead of marking it as successful; offline UUIDs will no longer be stored in the database as Mojang-verified UUIDs
- **No longer prompt non-existent passwords**: FLP will no longer tell players an auto-registered password that was never saved
- **Single source for premium status**: FLP keeps AuthMeReloaded's built-in premium verification disabled, retakes control after `/authme reload`, and pushes premium status changes to AuthMeReloaded's proxy plugin
- **Block AuthMeReloaded's `/premium` and `/freemium` commands**: Prompt players to use the corresponding FLP commands to avoid database and player state inconsistency
- **On Velocity**: Premium UUIDs are read from the player's original profile, and overwrites by AuthMeReloaded will no longer pollute them

### BungeeCord

- **Plugin available on newer BungeeCord versions**: Verified premium UUIDs are also sent alongside the traditional handshake; proxy connection classes are resolved at runtime

### SkinsRestorer

- **Custom skins no longer conflict with FLP**: Players' custom skins persist after rejoin
- **SR compatibility still works when `premiumUuid: false`**: FLP queries using the UUID obtained during verification instead of the UUID carried at connection time

### Other fixes

- Duplicate proxy messages on proxy backends no longer emit warning logs
- Preserve trailing newlines when rewriting `config.yml`
- Corrected description for `mojang-request-limit` (this option is functional)
- Build now enforces minimum Java version checks for each module; dependency upgrades will no longer silently raise the required Java version
- Build, CI and release now use the Gradle wrapper; Maven project files have been removed
- UUIDs verified by ProtocolSupport are no longer discarded

### Upgrade notes

- FLP modifies some AuthMeReloaded configurations to prevent compatibility issues: sets `enablePremium` to `true`; locks `premium.keepOfflineUuidCompatibility` of AuthMeBungee/AuthMeVelocity to `false`
- Proxy users are recommended to update FLP on both backend and frontend at the same time to avoid unexpected issues

### Known issues

- **Rare edge case**: Players disconnect while the AuthMeReloaded dialogue is open (backend logs `keepalive ... out-of-order`). This is currently identified as a server-side issue.

## v0.6.0

> This is a release focused on stability fixes.

### Bug Fixes

**Fixes for issues found in v0.5.0**

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
- **Plugin message hardening**: the login-action message type byte is validated on read — a malformed client plugin message now fails fast with an exception the message listeners catch instead of an uncaught `ArrayIndexOutOfBoundsException`

**Fixes for queued commands (pending relay)**

- Fixed a race where two conflicting console toggles (`/flp premium X` then `/flp cracked X`) could relay the stale captured value: the relay task now atomically removes the queue entry and sends the CURRENT queued value (`PendingRelayStore.removeToggle`), so the last command always wins (bukkit, folia and the Paper configure-phase self-relay path).
- A pending cracked toggle for a player who joins while nobody else is online is no longer silently dropped by the Paper configure listener (autoRegister skip): the entry stays queued and is relayed to the proxy once any player reaches the PLAY phase, so the proxy database is actually flipped to cracked.
- Folia relay tasks no longer lose a toggle/delete when the carrier player quits between the async online-check and the global-region execution: the task re-checks `isOnline()` before removing the queue entry, re-chains the retry if the carrier left, and re-queues + retries when the plugin-message send itself fails.
- The proxy no longer kicks a player who is ALREADY cracked when `/flp cracked` is relayed for them (bungee + velocity): a no-state-change toggle now behaves like the already-premium skip. This also respects `kick-toggle: false` and removes the misleading "premium removed" disconnect text on a no-op toggle.
- Toggle/delete results for backend-relayed commands are now mirrored back to the backend console via a new proxy → backend plugin message (`ToggleFeedbackMessage`, channel `fb-st`, carries a locale key + the proxy UUID validated against `allowed-proxies.txt`) — the admin no longer has to check the proxy log to learn whether the queued toggle succeeded.
- Retry-task accumulation bounded: `queueToggle`/`queueDelete` now report whether a NEW entry was created, and commands only schedule a retry task for new entries — an already-queued entry keeps its live task, which picks up overwritten values at send time.
- Known limitations (documented, unchanged by design): the pending queue is per-backend (a toggle queued on backend A is delivered when A has a player online, not on other backends); entries have no TTL — admin intent is preserved until delivered or until proxy support is disabled (`clearAll`).

### Changes

**/flp help restricted to operators**

- The bare `/flp` command now requires server operator permission: other senders receive the localized `no-permission` message

**Quieter update-check failures**

- Update-check network failures are now logged as a single WARN line with the failure reason instead of an INFO entry with a full stack trace (the trace moved to debug level)

**ProtocolLib async design record**

- The decision to keep the ProtocolLib login listener registered as an async handler is now documented in [`docs/en/PROTOCOLLIB-ASYNC-DESIGN.md`](docs/en/PROTOCOLLIB-ASYNC-DESIGN.md): why it is async, what compensates for that, what risk remains, what operators should do about the startup self-check warning, and what would make us revisit the decision
- A misleading comment in the ProtocolLib kick source was corrected (bukkit + folia)

### Reminder

For users of MySQL:  
The recommended value for `lifetime` is **1800** seconds. Values below 300 are now clamped to 300 with a startup warning.

## v0.5.0

### Pending proxy relay queue persistence

- The offline relay queue for proxy toggle/delete messages now survives restarts: messages are persisted to `pending-relay.json` (atomic rewrite), restored on startup, and corrupt files are moved aside instead of crashing the plugin. Toggles/deletes queued while nobody was online to carry them are now eventually delivered to the proxy once a player joins again.

### /flp toggle null-profile guards

- `PremiumCommand`/`CrackedCommand` (bukkit+folia, self & other paths): a null `loadProfile` result (SQL exception only) now sends the localized `database-error` message instead of throwing an NPE (self paths) or reporting hardcoded English / `player-unknown` (other paths) — "not found" was misleading for a database failure
- velocity/bungee `PluginMessageListener` change branch now reads `premium-warning` via the typed `config.getBoolean(...)` instead of an unguarded `(boolean) config.get(...)` cast, which threw a `ClassCastException` on string-typed values and was silently swallowed by the async scheduler

### /flp delete proxy fixes

- The `del-st` message now carries an `isSourceInvoker` flag: result feedback goes to the player who ran `/flp delete`, and to the proxy console when the command was issued from the console or relayed by a carrier — relay players are no longer spammed with delete results
- When no player is online to relay the delete message, the backend now queues the delete and retries every second until any player joins (aligned with the toggle-command behaviour) instead of silently dropping it; Folia uses a chained delayed task since it has no global repeating scheduler
- Proxy-side `/flp delete` now reports `database-error` when the database query itself fails (instead of lying "record not found"), fires the premium-toggle event on success, and re-checks the row when `deleteProfile` returns false to distinguish a real failure from a concurrent removal
- The standalone error path now uses the localized `database-error` message instead of hardcoded English text; reading legacy payloads (name only, no flag) degrades to "console invoker"
- Add `DeletePremiumMessageTest` covering the round trip, the console-relay flag and the legacy-format fallback

### Per-platform config templates

- BungeeCord/Velocity now generate `config.yml` from a dedicated `config-proxy.yml` template: backend-only keys (`verifyClientKeys`, `respectIpLimit`) are no longer present, and comments describe the proxy's role (decision maker: Mojang queries, database, force-login forwarding)
- Bukkit/Folia config comments now mark which keys lose effect (or only partially apply) when the server runs behind a proxy — `database`, `anti-bot`, Floodgate keys, JoinManagement keys and more
- ConfigRefresher preserves values for scalar keys written without a template value (e.g. `ServerRSAPublicKeyFile`)
- `loadFile` defaults now follow the selected config template (backend vs proxy), and `setConfigTemplate` fails fast when called after `load` instead of silently misbehaving

### Proxy premium row persistence

- Proxies now persist a `premium=true` row themselves after verifying an online-mode session (`ForceLoginManagement` null hook branch) — no longer relying solely on the backend's `SuccessMessage` ack, which AuthMe 6.0 proxy deployments never send (REGISTER action skips ForceLoginTask when the AuthMe record already exists; LOGIN action returns false from `forceLogin` after `AsynchronousJoin` bypasses)
- Verified-premium sessions where the auth plugin reports failure (`forceLogin` returns false) now still ack the proxy, restoring the ack persistence path
- Fixes #5: a player with a premium row can no longer be let in offline by `secondAttemptCracked` after a session expiry
- Add `ForceLoginManagementTest` covering proxy null-branch persistence, row upgrade semantics, AuthMe 6.0 bypass ack, success-path regression and cracked-path regression

### SQLite case-insensitive names

- SQLite `premium` table now creates `Name` with `COLLATE NOCASE`: Minecraft usernames are case-insensitive ("Steve" and "steve" are the same account), so a player can no longer end up with two rows (one premium, one cracked) that differ only by letter case — matching MySQL's default case-insensitive collation
- On startup, an existing case-sensitive `premium` table is migrated in one transaction (rename → recreate → copy → drop); the migration is idempotent, preserves all rows, and keeps one row per name variant when a database already contains premium + cracked rows that differ only by case (premium wins, otherwise the oldest row)
- Add `SQLiteStorageTest` covering case-insensitive lookup, case-variant duplicate rejection, legacy table migration and migration idempotency

### AsyncToggleMessage NPE on database failure

- `/premium` and `/cracked` toggles (BungeeCord + Velocity) no longer throw NullPointerException when the profile lookup fails (SQLite lock timeout, MySQL down, dropped connection). The task aborts, sends the new `database-error` message to the invoker and logs the abort — previously the command silently did nothing and only a stack trace appeared on the proxy console

### Other Bug Fixes

- Session verification aborts when the player disconnects mid-verification (e.g. the user cancels the login while Mojang is still answering): no more Mojang queries or ghost-kicks for players who are no longer connecting (`VerifyResponseTask` checks the Netty channel before proceeding)
- Velocity login sessions are now keyed by the connection's remote address instead of the connection object, fixing lost session state between `GameProfileRequest` (which exposes a different `InboundConnection` instance) and later events; the success-ack path no longer NPEs when the session is already gone
- Proxy toggle messages for admin operations on other players now use the existing `-other` message variants (the dedicated keys were removed)
- Bukkit's toggle relay no longer double-sends a pending toggle: `remove-if-present` guarantees at most one relay per toggle even when the configure listener or a retry already relayed it; Folia now queues and retries every second when no player is online, matching Bukkit's behaviour

### Logging & Diagnostics

- Added proxy-side debug logs for the premium toggle flow and `/flp` command diagnostics (registration result, invocation)

### Documentation

- README updates plus config-comment documentation(EFFECT-line)

## v0.4.0

### Paper Configure Phase autoRegister

- When uses proxy, Paper backend now auto-registers players during the configuration phase via `AsyncPlayerConnectionConfigureEvent`, running before AuthMe's listener — no more `HorriblePlayerLoginEventHack`
- UUID mismatch guard prevents cracked players from being auto-registered as premium
- Startup log confirms configure listener registration for diagnostics

### Pending Toggle Self-Relay

- PLAY-phase self-relay replaces old retry mechanism — when a player toggles premium/cracked mode, the toggle reliably applies on next join
- Respects kick-toggle config, bypasses UUID guard for pending toggles, handles offline toggle when no relay player
- Proxy-side kick on toggle: correctly kicks the target player and resolves premium UUID
- Applied to Folia module as well

### AuthMe Cleanup on Cracked Switch

- When a player switches from premium to cracked, FLP now thoroughly cleans AuthMe records: clears premium flag, force-unregisters accounts (supports AuthMe 5.x and 6.0), purges in-memory caches
- Second-chance cleanup via `ensureNotPremium()` on cracked login

### Bug Fixes

- Remove duplicate `[FLP]` prefix from log messages
- Hide player name tab completion when missing `.other` permission
- Enforce command permissions for `/flp` subcommands
- Fix `PremiumCommand` kick message using wrong language key (`remove-premium` → `add-premium`)
- Fix premium-warning message only for self-command, close preJoin login dialog on cracked→premium switch
- Fix SQLite native library loading on Velocity/BungeeCord by removing `org.sqlite` relocation
- Fix `CrackedCommand` NPE in proxy mode — defer `getStorage()` until after forward check
- `mojang-retry-count` and `mojang-retry-delay` now apply to ALL Mojang API calls (both proxy-side Name→UUID lookup and backend session verification), with exponential backoff

### Documentation

- Add more info about Velocity and BungeeCord to README
- Add platform labels to issue template
- Explain AuthMe cleanup behavior when switching to cracked mode

## v0.3.1 (Hotfix)

### Bug Fix(Critical)

- Fix `ClassCastException: Integer cannot be cast to Long` on login — SnakeYAML parses small numeric config values (e.g. `mojang-retry-delay: 500`) as `Integer`, but the code directly cast to `(long)` which crashes. Use `Number.longValue()`/`intValue()` for safe conversion across all numeric config reads

## v0.3.0

### Bug Fixes(Major)

- Anti-bot — 6 bug fixes: clock jump back no longer throws in TickingRateLimiter, batch expire stale records, compareTo uses correct expireTime, global rate limit checked before per-IP, periodic cleanup every 100 connections, sanitize usernames in log messages
- Fix `forwardSkin: false` not working on Paper — PaperCacheListener now checks config before setting skin
- Fix SkinsRestorer skin overwritten by Paper filledProfileCache — set empty placeholder textures to prevent `complete(true)` pulling stale skin
- Guard against null `floodgate_data_handler` in ProtocolLib pipeline — prevent NPE if Floodgate renames/removes the handler

### Code Cleanup

- Remove 22 redundant default values from config `getXxx()` calls across all modules (ConfigRefresher guarantees keys exist)

### Session Retry Improvement

- Exponential backoff for Mojang session retry

### Build

- Centralize 19 shared dependency versions into parent pom.xml properties
- Add FloodgateServiceTest (17 tests) and FloodgateManagementTest (15 tests)
- Upgrade maven-compiler-plugin 3.13.0 → 3.15.0

## v0.2.1

### Version Format Unification

- All plugin descriptors (plugin.yml, bungee.yml, velocity-plugin.json) now use `${revision}-${git.commit.id.abbrev}` format consistently
- Removed redundant `ManifestResourceTransformer` from bungee/velocity shade config
- Removed `META-INF/MANIFEST.MF` exclusion from shade filters that was causing MANIFEST loss

## v0.2.0

### Config Refresher

- Restore comments and keys in config.yml from the template each startup, keeping the config file aligned with the latest version and more portable

### Log Output Optimization

- Add `debug: false` option to config.yml to reduce log verbosity

### Git Hash in JAR Names

- Include git commit hash in JAR file names and MANIFEST to make file versions clearer

### Bug Fixes

- Fix `sslMode` and `allowPublicKeyRetrieval` using invalid `=` syntax

### Docs

- Optimized the configuration file structure and comments

## v0.1.2

> This version features the most comprehensive support for AuthMe. Users using **FLP with AuthMe** are RECOMMENDED to update as soon as possible. Subsequent versions will focus on **other bug fixes and user experience improvements**.

### AuthMe 6.0 Auto-Integration

- FLP automatically takes over AuthMe 6.0 premium verification — no manual `enablePremium=true` needed
- `forceEnablePremium()`: sets `enablePremium=true` via AuthMe's Settings API and persists to config.yml
- `unregisterPremiumPacketListener()`: unregisters AuthMe's PacketEvents listener so FLP is the sole verifier
- Lazy re-assert on each premium login to handle `/authme reload` re-registering the listener

### AuthMe 6.0 First-Time Premium Fix

- Fix preJoin dialog not skipped for first-time premium players (getAuth()==null case)
- Pre-create AuthMe DB record via saveAuth() + updatePremiumUuid() during LOGIN phase
- Fix premium session not persisted after first login (breaks reconnect)
- ForceLoginTask now saves onlinemodePreferred=true even when forceLogin returns ALREADY_AUTHENTICATED

### Other Bug Fixes

- Fix version check showing "unknown" instead of actual version (add Implementation-Version to JAR manifest)
- Fix `/flp cracked` command showing wrong message when targeting another player
- Fix START packet forwarded after source.kick() causing vanilla server to overwrite FLP's kick message

## v0.1.1

### AuthMe 6.0 preJoin Fix

- Fix AuthMe 6.0 preJoin dialog blocking premium players
- Fix the deadlock where the dialog blocked the connection before the premium flag could be set
- Add startup validation: ERROR log when AuthMe 6.0 preJoin is enabled but `enablePremium` is disabled

### Tab Completion

- Add tab completion for `/flp` command subcommands (bukkit + folia)

### Language Files

- Fix `messages_zh.yml` was not saved to the plugin configuration directory

### Build

- Add version number to JAR filenames

## v0.1.0

> **v0.1.0 marks the first major release of FastLoginPlus** — a consolidated upgrade covering new platforms, security hardening, and important bug fixes accumulated since the fork. **But this is NOT a stable release**.

### Folia Platform Support

- Add Folia as a separate module with FoliaScheduler
- The stability on Folia still requires long-term observation and verification

### Proxy SQLite Support

- Add SQLite support for BungeeCord with sqlite-jdbc dependency and shading
- Add missing org.sqlite relocation in Velocity for class isolation

### Automatic Update Check

- Add UpdateChecker for GitHub Releases with startup and periodic checks
- OP players receive update notifications on login
- Config option: check-update (default: true)

### FastLoginAntiBotEvent

- Add FastLoginAntiBotEvent interface in core (exposes address/username/action)
- Fire event on Block/Ignore actions, allow cancellation to bypass

### Multi-Layer Anti-Bot Upgrade

- PerIpRateLimiter: dual-window (burst + long) per-IP rate limiting
- IpBanManager: temporary IP ban with auto-expiration
- TrustedIpSet: immutable whitelist bypassing all anti-bot checks
- WindowCounter: thread-safe dual-window counter per IP
- AntiBotService: refactored as multi-layer orchestrator (trusted IP → ban check → per-IP limit → global limit)
- New config keys: per-ip-connections, per-ip-expire, burst-limit, burst-window, ban-duration, trusted-ips

### Bug Fixes

- Fix chunk rendering race condition on first login (fixes TuxCoding/FastLogin#1358)
- Add lock to SQLiteStorage.deleteProfile for thread safety
- Unify version management — use ${project.version}

## v0.0.7

### Command Namespace Unification

- Unify all commands under `/flp` namespace: `/premium` → `/flp premium`, `/cracked` → `/flp cracked`, `/fldelete` → `/flp delete`
- Remove legacy standalone command definitions from plugin.yml
- Update command references in config comments and user-facing messages

### PlaceholderAPI Placeholders

- Add `%fastloginplus_is_premium%` placeholder (returns true/false)
- Add `%fastloginplus_floodgate%` placeholder (returns Java/Bedrock/Linked/Unknown)

### Bedrock Player Support

- Upgrade Geyser 2.2.1→2.10.1 and Floodgate 2.2.3→2.2.5
- Add Bedrock player support section to README with Geyser/Floodgate guidance

### Code Cleanup

- Remove dead version-detection and reflection code from AsyncScheduler

## v0.0.6

- Change `autoRegister`, `premiumUuid`, `nameChangeCheck` defaults to `true`
- Change `/cracked` default permission from `true` to `op`
- Optimize config comments and sections
- Clarify `offline-whitelist` only controls access, not registration
- Add `LOGIN-FLOW.md` documentation

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

## v0.0.4

### Session Retry

Added automatic retry for Mojang session server verification (Spigot+ProtocolLib only):

- When `hasJoined` fails due to a network error (IOException), the plugin now retries up to `mojang-retry-count` times (default 3) with `mojang-retry-delay` ms between attempts (default 1000)
- HTTP 204 (auth rejection) is NOT retried — only network errors
- New kick message `session-retry-exhausted` shown when all retries fail

### Log Improvement

Improved login flow log readability:

- Replaced raw ProtocolLib packet dump with human-readable messages
- Moved internal details (packet type override, encryption setup) to DEBUG level
- Added "Verifying session for {player}" log at session check start

### Dependency

- Mockito 5.17.0 → 5.18.0 (fixes JDK 25 ByteBuddy compatibility)

## v0.0.3

### SkinsRestorer Compatibility

Fixed FastLogin overriding SkinsRestorer custom skins ([TuxCoding/FastLogin#1347](https://github.com/TuxCoding/FastLogin/issues/1347)):

- Player skins set via SkinsRestorer's `/skin` command are now preserved — FastLoginPlus skips its own skin when SR has a custom skin for the player
- Added `SkinsRestorerCompat` helper using SR's official API (`PlayerStorage.getSkinIdOfPlayer`)
- SkinsRestorer listed as `softdepend` in `plugin.yml` to ensure correct load order

### Bug Fixes

- **`forwardSkin: false` not working on Paper**: The `PaperCacheListener` was always registered on Paper regardless of the `forwardSkin` config. Now respects the setting.

## v0.0.2

### Offline Whitelist

Replaced `switchMode` with a new standalone **offline-whitelist** feature:

- `offline-whitelist: true` — Only players already in the database can join as cracked/offline
- New offline (cracked) players are kicked with a localized message
- Premium players are unaffected — auto-detected via Mojang API and allowed to join
- Existing cracked players in the database continue to join normally
- The Mojang API check is automatically triggered when `offline-whitelist` is enabled (no need to enable `autoRegister` or `nameChangeCheck` separately)

### Removed

- **`switchMode`** config option removed — replaced by `offline-whitelist`

## v0.0.1

First independent release of FastLoginPlus, forked from [FastLogin](https://github.com/TuxCoding/FastLogin) with enhancements.

### Project Renaming

- Renamed to **FastLoginPlus**, Maven artifact `fastlogin` → `fastloginplus`
- Independent versioning starting from v0.0.1
- Java package names (`com.github.games647.fastlogin`) unchanged for upstream compatibility

### Bug Fixes

- **switchMode kicked new premium players**: When `switchMode` was enabled, premium players joining for the first time were incorrectly kicked ([#1359](https://github.com/TuxCoding/FastLogin/issues/1359)). Now premium players are properly detected via Mojang API and allowed to join. (Note: `switchMode` has since been replaced by `offline-whitelist` in v0.0.2)

### SQLite Concurrency

- **WAL mode** — Write-Ahead Logging for better concurrent read/write under proxy architecture
- **Busy timeout** — 5-second wait instead of instant `SQLITE_BUSY` errors
- **Thread-safe operations** — `ReentrantLock` on all `loadProfile` / `save` calls

### fldelete Enhancement

The upstream `fldelete` was bare-bones (hardcoded English, no premium protection, broken under BungeeCord). Fully rewritten:

- Localized message strings (multi-language support)
- Premium player protection — cannot delete online-mode player records
- BungeeCord support via PluginMessage forwarding
- Fires `BukkitFastLoginPremiumToggleEvent` on successful deletion

### Multi-language System

- Built-in **English** (`messages_en.yml`) and **Chinese** (`messages_zh.yml`) language files
- `language` option in `config.yml` to select language (`en` / `zh` / custom)
- Custom languages supported — set any value (e.g. `ja`), plugin loads `messages_ja.yml`, falls back to English if missing
- Auto-detects missing keys on startup and fills them from the English default
- Config comments in bilingual (English + Chinese)
