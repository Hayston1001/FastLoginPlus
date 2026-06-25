# FastLoginPlus

[中文](README_zh.md)

> **Actively maintained fork of [FastLogin](https://github.com/TuxCoding/FastLogin)** with updates, bug fixes, and feature enhancements.

FastLoginPlus automatically detects whether a Minecraft player owns a paid (premium) account. If so, they can skip offline-mode authentication — no password needed.

## Features

* Auto-detect and auto-login premium (paid) accounts
* Auto-register new premium players
* Premium UUID support & skin forwarding
* Username change detection with automatic DB update
* Multi-platform: Bukkit (Spigot/Paper) / BungeeCord / Velocity
* Bedrock player support via Floodgate / Geyser
* Built-in English & Chinese language files, with custom language support
* PlaceholderAPI integration
* Async operations for high performance
* No client mods required

## What's New in FastLoginPlus

Compared to the original FastLogin, this fork includes:

* **SQLite WAL mode** — Write-Ahead Logging for better concurrent read/write performance under proxy architecture
* **SQLite busy timeout** — Configurable 5s wait instead of instant `SQLITE_BUSY` errors
* **Thread-safe SQLite operations** — `ReentrantLock` guarding all profile load/save operations
* **switchMode bug fix** — New premium players are no longer incorrectly kicked when `switchMode` is enabled ([#1359](https://github.com/TuxCoding/FastLogin/issues/1359))
* **fldelete enhanced** — Localized messages, premium player protection, BungeeCord support
* **Multi-language system** — Built-in `en`/`zh`, config-driven, auto-fills missing keys on startup
* **Bilingual config** — `config.yml` comments in both English and Chinese

## Requirements

* **Java**: 8+ (Spigot), 17+ (BungeeCord / Velocity), 21+ recommended
* **Server** in offline mode (`online-mode=false`)
* **Spigot** (or Paper) 1.8.8+ with [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) (5.3+) or [ProtocolSupport](https://www.spigotmc.org/resources/protocolsupport.7201/)
* **BungeeCord** (or Waterfall) / **Velocity** proxy
* An auth plugin (see below)

### Supported Auth Plugins

#### Spigot / Paper
* [AuthMe](https://dev.bukkit.org/bukkit-plugins/authme-reloaded/) (5.x)
* [CrazyLogin](https://dev.bukkit.org/bukkit-plugins/crazylogin/)
* [LoginSecurity](https://dev.bukkit.org/bukkit-plugins/loginsecurity/)
* [LogIt](https://github.com/games647/LogIt)
* [UltraAuth](https://dev.bukkit.org/bukkit-plugins/ultraauth-aa/)
* [xAuth](https://dev.bukkit.org/bukkit-plugins/xauth/)
* [Passky](https://github.com/Passky)

#### BungeeCord / Waterfall
* [BungeeAuth](https://www.spigotmc.org/resources/bungeeauth.493/)

## Installation

### Spigot / Paper

1. Install ProtocolLib or ProtocolSupport
2. Download `FastLoginPlusBukkit.jar` and place it in `plugins/`
3. Set `online-mode=false` in `server.properties`

### BungeeCord / Waterfall or Velocity

Install the plugin on **both** the proxy and the backend server:

1. Enable proxy support in your backend server config (`spigot.yml` or `paper.yml`)
2. Restart the backend server
3. Configure `allowed-proxies.txt` in the FastLoginPlus plugin folder:
   - **BungeeCord**: Add your `stats`-id from BungeeCord config
   - **Velocity**: The plugin auto-generates `proxyId.txt`
4. Enable IP forwarding in your proxy config
5. Configure database settings in `config.yml` (BungeeCord: `mysql`; Velocity: `mariadb`)
6. Set `online-mode=false` on both proxy and backend
7. **Always** firewall your backend so it's only accessible through the proxy

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/premium [player]` | Mark yourself (or another) as premium | `fastloginplus.bukkit.command.premium` |
| `/cracked [player]` | Mark yourself (or another) as cracked | `fastloginplus.bukkit.command.cracked` |
| `/fldelete <player>` | Delete a player record from the database | `fastloginplus.bukkit.command.delete` |

## Permissions

| Permission | Description | Default |
|-----------|-------------|---------|
| `fastloginplus.bukkit.command.premium` | Mark self as premium | true |
| `fastloginplus.bukkit.command.premium.other` | Mark others as premium | op |
| `fastloginplus.bukkit.command.cracked` | Mark self as cracked | true |
| `fastloginplus.bukkit.command.cracked.other` | Mark others as cracked | op |
| `fastloginplus.bukkit.command.delete` | Delete player records | op |

## PlaceholderAPI

On Spigot, this plugin exports `%fastloginplus_status%`. Possible values: `Premium`, `Cracked`, `Unknown`.

> In BungeeCord environments, the status may briefly be `Unknown` for a few milliseconds after join.

## Language Support

Set `language` in `config.yml`:

```yaml
language: en   # English
language: zh   # 中文
```

Custom languages are supported — set any value (e.g. `language: ja`), and the plugin will load `messages_ja.yml`. If the file doesn't exist, it falls back to English. Missing keys are auto-filled on every startup.

## Network Requests

This plugin contacts:
* `https://api.mojang.com` — UUID lookup for premium detection
* `https://sessionserver.mojang.com` — Account ownership verification

## License

This project is licensed under the [MIT License](LICENSE).

Originally created by [games647](https://github.com/TuxCoding/FastLogin).
Fork maintained by [Hayston](https://github.com/Hayston1001).