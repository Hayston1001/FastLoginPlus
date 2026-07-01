# FastLoginPlus

[中文→](README_zh.md)

> **Auto-detect and login premium Minecraft players on offline-mode servers** — no password needed, no client mods required. Actively maintained fork of [FastLogin](https://github.com/TuxCoding/FastLogin).

Many Minecraft servers run in "offline mode" (no Mojang authentication) to allow cracked clients, but this forces all players — including those with paid accounts — to type a password every time they join. FastLoginPlus checks each player against Mojang's API on login: if they own the game, they skip the auth plugin entirely and get their real UUID and skin automatically.

## Features

### Core (from FastLogin)

* Auto-detect premium accounts via Mojang API — skip auth plugin login
* Premium UUID and skin forwarding
* Auto-register new premium players
* BungeeCord / Velocity proxy support
* Bedrock player support via Geyser / Floodgate

### Improvements (new in FastLoginPlus)

* **AuthMe 6.0 compatibility** — auto-detects AuthMe version without user config
* **Offline Whitelist** — block unknown cracked players, allow premium via Mojang API. Replaces upstream `switchMode` which kicked new premium players
* **Multi-layer anti-bot** — per-IP rate limiting, burst detection, temporary IP ban, trusted IP whitelist, and `FastLoginAntiBotEvent` for plugin integration
* **Folia support** — dedicated module with Folia-compatible scheduler (`Entity.getScheduler()`, `Bukkit.getAsyncScheduler()`)
* **Auto update check** — checks GitHub Releases on startup and periodically; notifies OPs in-game when a new version is available
* **Multi-language** — built-in English and Chinese, custom language files supported, bilingual config comments
* **SQLite on proxy platforms** — BungeeCord and Velocity now bundle SQLite JDBC driver; upstream only supports MySQL/MariaDB on proxies
* **Session retry** — Mojang verification retries on network errors instead of failing immediately
* **SkinsRestorer compatibility** — no longer overrides skins set via SkinsRestorer
* **Log readability** — human-readable login flow messages instead of raw packet dumps

## Quick Start

**Spigot / Paper:** install ProtocolLib → drop `FastLoginPlusBukkit.jar` in `plugins/` → set `online-mode=false`

**Folia:** drop `FastLoginPlusFolia.jar` in `plugins/` → set `online-mode=false`

**BungeeCord / Velocity:** install on both proxy and backend → configure `allowed-proxies.txt` → enable IP forwarding → set `online-mode=false` on both

## Requirements

| Platform | Java | Notes |
|----------|------|-------|
| Spigot / Paper | 8+ | Requires [ProtocolLib 5.3+](https://www.spigotmc.org/resources/protocollib.1997/) or [ProtocolSupport](https://www.spigotmc.org/resources/protocolsupport.7201/) |
| Folia | 17+ | Requires ProtocolLib 5.3+ |
| BungeeCord / Waterfall | 17+ | — |
| Velocity | 17+ | — |

An auth plugin is required on the backend (e.g. AuthMe, LoginSecurity, CrazyLogin). See [full list](https://github.com/TuxCoding/FastLogin#supported-auth-plugins).

## Commands & Permissions

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/flp premium [player]` | Mark as premium | `fastloginplus.bukkit.command.premium` | true |
| `/flp cracked [player]` | Mark as cracked | `fastloginplus.bukkit.command.cracked` | op |
| `/flp delete <player>` | Delete player record | `fastloginplus.bukkit.command.delete` | op |

Add `.other` suffix for targeting other players (default: op).

## PlaceholderAPI

| Placeholder | Values | Description |
|---|---|---|
| `%fastloginplus_status%` | `Premium`, `Cracked`, `Unknown` | Authentication status |
| `%fastloginplus_is_premium%` | `true`, `false` | Whether the player passed premium verification |
| `%fastloginplus_floodgate%` | `Java`, `Bedrock`, `Linked`, `Unknown` | Connection platform (Java vs Bedrock via Geyser/Floodgate) |

## License

[MIT](LICENSE) · Originally by [games647](https://github.com/TuxCoding/FastLogin) · Maintained by [Hayston](https://github.com/Hayston1001)
