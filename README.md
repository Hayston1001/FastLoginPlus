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

## Bedrock Player Support (Geyser / Floodgate)

FastLoginPlus works with [Geyser](https://geysermc.org/) to allow Bedrock players to join your offline-mode Java server.

- **Geyser only** — Bedrock players join without Xbox authentication. FLP treats them as regular Java players; premium auto-login works if the username matches a paid Java account.
- **Geyser + [Floodgate](https://geysermc.org/floodgate/)** (recommended) — Bedrock players authenticate via Xbox Live, and their usernames are prefixed (e.g. `Steve` → `.Steve`). This prevents FLP from mistaking a Bedrock player for a premium Java account and avoids username conflicts between platforms.

> **Recommendation:** Install Floodgate alongside Geyser for better security and identity separation. FLP does not require Floodgate to function, but it is strongly recommended when both Java and Bedrock players share the same server.

> **Version requirements:** Geyser requires **Java 21+** to run. Geyser-Spigot requires a Paper/Spigot server on **1.20.5 or above**. Servers below 1.20.5 can still use Geyser by installing [ViaVersion](https://viaversion.com/) on the backend and running Geyser on a proxy (Velocity/BungeeCord), or by using Geyser-Standalone with ViaVersion. ViaVersion allows the server to accept newer Java clients, which Geyser uses as the translation target. See [Geyser supported versions](https://geysermc.org/wiki/geyser/supported-versions/) for details.

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
