# FastLoginPlus

[中文](README_zh.md)

> **Actively maintained fork of [FastLogin](https://github.com/TuxCoding/FastLogin)** — auto-detect and login premium Minecraft accounts on offline-mode servers.

For base features, platform support, and technical details, see [FastLogin's README](https://github.com/TuxCoding/FastLogin).

## What's Different

- **AuthMe 6.0 compatibility** — auto-detects AuthMe version, adapts premium flow and command namespace (`/flp`) without user config.
- **Offline Whitelist** — block unknown cracked players, allow premium via Mojang API. Replaces the upstream `switchMode` which had issues with new premium players being kicked.
- **Multi-language** — built-in English and Chinese, custom language files supported, bilingual config comments.
- **SQLite concurrency** — WAL mode, busy timeout, thread-safe operations with `ReentrantLock`.
- **Session retry** — Mojang session verification retries on network errors instead of failing immediately.
- **SkinsRestorer compatibility** — no longer overrides skins set via SkinsRestorer.
- **`fldelete` rewrite** — localized messages, premium player protection, BungeeCord support.
- **Log readability** — human-readable login flow messages instead of raw packet dumps.

## Quick Start

**Spigot / Paper:** install ProtocolLib → drop `FastLoginPlusBukkit.jar` in `plugins/` → set `online-mode=false`

**BungeeCord / Velocity:** install on both proxy and backend → configure `allowed-proxies.txt` → enable IP forwarding → set `online-mode=false` on both → [full guide](https://github.com/Hayston1001/FastLoginPlus/wiki)

For detailed installation steps, see [FastLogin's install guide](https://github.com/TuxCoding/FastLogin#how-to-install).

## Commands & Permissions

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/premium [player]` | Mark as premium | `fastloginplus.bukkit.command.premium` | true |
| `/cracked [player]` | Mark as cracked | `fastloginplus.bukkit.command.cracked` | op |
| `/fldelete <player>` | Delete player record | `fastloginplus.bukkit.command.delete` | op |

Add `.other` suffix for targeting other players (default: op).

> **Note:** When AuthMe 6.0+ is detected, `/premium` and `/cracked` are registered under `/flp` (e.g. `/flp premium`) to avoid command conflict. `/fldelete` is unchanged.

## PlaceholderAPI

Exports `%fastloginplus_status%` — values: `Premium`, `Cracked`, `Unknown`.

## License

[MIT](LICENSE) · Originally by [games647](https://github.com/TuxCoding/FastLogin) · Maintained by [Hayston](https://github.com/Hayston1001)
