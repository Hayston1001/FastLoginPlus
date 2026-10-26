# FastLoginPlus

English | [简体中文](README_zh.md)

[![Modrinth downloads](https://img.shields.io/modrinth/dt/dmpkLjdL?style=flat-square&logo=modrinth&label=Modrinth&color=00AF5C)](https://modrinth.com/plugin/fastloginplus)
[![Build](https://img.shields.io/github/actions/workflow/status/Hayston1001/FastLoginPlus/gradle.yml?branch=main&style=flat-square&logo=githubactions&logoColor=white&label=Build)](https://github.com/Hayston1001/FastLoginPlus/actions/workflows/gradle.yml)
[![Release](https://img.shields.io/github/actions/workflow/status/Hayston1001/FastLoginPlus/release.yml?style=flat-square&logo=githubactions&logoColor=white&label=Release)](https://github.com/Hayston1001/FastLoginPlus/actions/workflows/release.yml)
[![MIT](https://img.shields.io/badge/License-MIT-2563EB?style=flat-square)](LICENSE)

[![modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg)](https://modrinth.com/plugin/fastloginplus)

**Connecting verified Minecraft identities with your server's authentication plugin.** An actively maintained fork of [FastLogin](https://github.com/TuxCoding/FastLogin).

FastLoginPlus can provide passwordless login for verified Java owners and supported Floodgate players while keeping password authentication for other accounts. Identity verification, server policy and local account ownership determine the login method; a premium username alone never proves ownership.

> The documentation targets the forthcoming **1.0.0**. Older release artifacts may not include all documented features.

## Features

### Core features (from FastLogin)

* Automatically detect premium accounts through the Mojang API
* Automatically register premium players
* Premium UUID and skin forwarding

### New features (from FastLoginPlus)

* **[AuthMeReloaded](https://modrinth.com/plugin/authmereloaded) 6.0 compatibility**: Automatically detects the AuthMeReloaded version, with no user configuration required
* **[SkinsRestorer](https://github.com/SkinsRestorer/SkinsRestorer) compatibility**: No longer overrides SkinsRestorer's custom skins
* **Folia support**: A dedicated module using Folia-compatible schedulers
* **Initial Fabric support**: Under development, with Velocity + [EasyAuth](https://github.com/NikitaCartes/EasyAuth) 3.4.x integration and gradually expanding coverage
* **Proxy-side SQLite support**: BungeeCord and Velocity bundle the SQLite JDBC driver, suitable for small single-proxy servers
* **Enhanced anti-bot protection**: Per-IP rate limits, burst detection, temporary bans and a trusted IP allowlist
* **Multiple languages**: Supports switching between custom language files
* **Platform-specific configuration templates**: Bukkit/Fabric and BungeeCord/Velocity each generate their own config files
* **Session verification retries**: Automatically retries Mojang verification on network errors instead of failing immediately

## Contribute and get help

[![Report a problem](https://img.shields.io/badge/Report%20a%20problem-D97706?style=flat-square&logo=github&logoColor=white)](docs/en/operations/reporting.md)
[![Contributing](https://img.shields.io/badge/Contributing-2EA44F?style=flat-square&logo=github&logoColor=white)](CONTRIBUTING.md)
[![Build from source](https://img.shields.io/badge/Build%20from%20source-0969DA?style=flat-square&logo=gradle&logoColor=white)](docs/en/development/build.md)

## Star history

<a href="https://www.star-history.com/?repos=hayston1001%2Ffastloginplus&type=date&releases=&legend=bottom-right">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=hayston1001/fastloginplus&type=date&theme=dark&legend=bottom-right" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=hayston1001/fastloginplus&type=date&legend=bottom-right" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=hayston1001/fastloginplus&type=date&legend=bottom-right" />
 </picture>
</a>

## License

[MIT](LICENSE). Originally by [games647](https://github.com/TuxCoding), maintained by [Hayston1001](https://github.com/Hayston1001)
