# Velocity and Fabric/EasyAuth

This is a **server-side Minecraft 26.2** integration. Java clients do not need an FLP mod. It requires Velocity modern forwarding through FabricProxy-Lite, plus EasyAuth on the backend. It is not standalone Fabric authentication and does not support BungeeCord forwarding.

## Prerequisites

- Java 25 on the Fabric server; Minecraft 26.2, Fabric Loader >=0.19.3 and compatible Fabric API.
- FabricProxy-Lite and EasyAuth. The reference EasyAuth version is **3.4.4**; the allowed 3.4.x loader range is not proof of identical behavior for every build.
- Local SQLite for the supported proxy/EasyAuth storage combination. Other EasyAuth stores emit a warning and are unverified, especially their distinction between a missing account and a failed read.
- Backups of EasyAuth, FLP records and world/player data. Read [UUID implications](../accounts/uuid.md) before enabling bindings.

## Install

1. Stop both sides. Put the Velocity FLP JAR in the proxy's `plugins/`. Put FLP's **Fabric 26.2** JAR, Fabric API, FabricProxy-Lite and EasyAuth in the backend's `mods/`. Do not install Bukkit/Folia FLP or the conflicting FabricProxy mod there.
2. Set Velocity `online-mode = false` and `player-info-forwarding-mode = "modern"`. Set backend `server.properties` to `online-mode=false`. Set `secret` in `config/FabricProxy-Lite.toml` to Velocity's forwarding secret.
3. Start once to generate files, then stop. In EasyAuth 3.4.4, the main options below live in `config/EasyAuth/main.conf`; the two Extended options live in `config/EasyAuth/extended.conf`. These are HOCON files, not FLP YAML. Review the generated configuration and set:

   | EasyAuth option | Required value |
   | --- | --- |
   | `premium-auto-login` | `false` |
   | `offline-by-default` | `false` |
   | `floodgate-auto-login` | `false` |
   | `session-timeout` | `-1` |
   | `enable-global-password` | Prefer `false`; if enabled, `single-use-global-password` must be `true` |
   | Extended `skip-all-auth-checks` | `false` |
   | Extended `allow-all-packets` | `false` |

   These are EasyAuth settings, not keys to add to FLP's YAML. FLP checks the loaded policy; it does not implement EasyAuth hot reload.
4. Edit `config/fastloginplus-fabric.yml`. Set `language`, optional `debug`, and `allowed-proxy-ids` to the UUID from Velocity's `plugins/fastloginplus/proxyId.txt`. A comma-separated string is supported; YAML block/flow lists are also accepted and normalized on startup. Do not substitute the forwarding secret.
5. Configure Java/Bedrock policies in the **proxy's** FLP `config.yml`. The Fabric YAML contains only backend-local settings, not a second policy set. No extra Fabric-mode switch is needed in the current proxy implementation.
6. Restrict direct backend access, restart both sides, check component/policy/storage warnings, and complete the [verification checklist](verification.md).

## Expected behavior

A trusted verified Java connection can use passwordless login only when policy and the EasyAuth account allow it. A password account without consent keeps its password. Its logged-in owner can use `/flp premium`, follow any proxy confirmation, and reconnect manually after consent is recorded. The conversion keeps the existing password dormant. There is no transfer, cookie exchange or ten-minute local binding window in the current flow.

`premiumUuid: false` can keep an offline connection UUID while the verified Mojang UUID still identifies the EasyAuth premium owner. This does not migrate player data. A successful name-follow operation must match the verified account identity and cannot overwrite another name's account.

Bedrock support is deliberately narrower: an unlinked signed Floodgate connection may auto-login to an **existing completed** EasyAuth account with the same identity and a stored credential. New Bedrock account creation, linked login and premium conversion are not supported by this path. See [Bedrock integration](../integrations/bedrock.md).

Changing backend FLP settings requires a restart. `/flp reload` and hot reload are unsupported. Commands from the Fabric console need an online carrier; there is no classic durable relay queue here.

Platform setup references: [Velocity forwarding](https://docs.papermc.io/velocity/player-information-forwarding/), [FabricProxy-Lite](https://github.com/OKTW-Network/FabricProxy-Lite) and [EasyAuth 3.4.4 configuration source](https://github.com/NikitaCartes/EasyAuth/tree/3.4.4/src/main/java/xyz/nikitacartes/easyauth/config). Do not substitute EasyAuth 4.x instructions for this version's policy/API checks.
