# FastLoginPlus

[中文](README_zh.md)

> **Actively maintained fork of [FastLogin](https://github.com/TuxCoding/FastLogin)** with bug fixes, performance improvements, and new features.

Auto-detect premium (paid) Minecraft accounts on offline-mode servers — skip authentication, no password needed.

## Features

* Auto-detect, auto-login, and auto-register premium accounts
* Premium UUID & skin forwarding, username change detection
* **Offline whitelist** — block unknown cracked players, allow premium via Mojang API
* Multi-platform: Bukkit (Spigot/Paper) / BungeeCord / Velocity
* Bedrock support via Floodgate / Geyser
* SQLite WAL mode with thread-safe operations (ReentrantLock + busy timeout)
* Built-in English & Chinese, custom language support, bilingual config
* PlaceholderAPI integration, fully async, no client mods required

## Requirements

* **Java** 8+ (Spigot), 17+ (BungeeCord / Velocity), 21+ recommended
* Server in **offline mode** (`online-mode=false`)
* [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) (5.3+) or [ProtocolSupport](https://www.spigotmc.org/resources/protocolsupport.7201/)
* An auth plugin: [AuthMe](https://dev.bukkit.org/bukkit-plugins/authme-reloaded/) · [LoginSecurity](https://dev.bukkit.org/bukkit-plugins/loginsecurity/) · [CrazyLogin](https://dev.bukkit.org/bukkit-plugins/crazylogin/) · [xAuth](https://dev.bukkit.org/bukkit-plugins/xauth/) · [Passky](https://github.com/Passky) · [BungeeAuth](https://www.spigotmc.org/resources/bungeeauth.493/)

## Quick Start

**Spigot / Paper:** install ProtocolLib → drop `FastLoginPlusBukkit.jar` in `plugins/` → set `online-mode=false`

**BungeeCord / Velocity:** install on both proxy and backend → configure `allowed-proxies.txt` → enable IP forwarding → set `online-mode=false` on both → [full guide](https://github.com/Hayston1001/FastLoginPlus/wiki)

## Commands & Permissions

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/premium [player]` | Mark as premium | `fastloginplus.bukkit.command.premium` | true |
| `/cracked [player]` | Mark as cracked | `fastloginplus.bukkit.command.cracked` | true |
| `/fldelete <player>` | Delete player record | `fastloginplus.bukkit.command.delete` | op |

Add `.other` suffix for targeting other players (default: op).

## PlaceholderAPI

Exports `%fastloginplus_status%` — values: `Premium`, `Cracked`, `Unknown`.

## License

[MIT](LICENSE) · Originally by [games647](https://github.com/TuxCoding/FastLogin) · Maintained by [Hayston](https://github.com/Hayston1001)
