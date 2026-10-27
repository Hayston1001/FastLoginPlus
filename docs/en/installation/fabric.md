# Velocity and Fabric/EasyAuth

This is a **server-side Minecraft 26.2** integration. Java clients do not need an FLP mod. It requires Velocity modern forwarding through FabricProxy-Lite, plus EasyAuth on the backend. It is not standalone Fabric authentication and does not support BungeeCord forwarding.

## Prerequisites

- Java 25 on the Fabric server; Minecraft 26.2, Fabric Loader >=0.19.3 and compatible Fabric API.
- FabricProxy-Lite and EasyAuth. The reference EasyAuth version is **3.4.4**; the allowed 3.4.x loader range is not proof of identical behavior for every build.
- Local SQLite for the supported proxy/EasyAuth storage combination. Other EasyAuth stores emit a warning and are unverified, especially their distinction between a missing account and a failed read.
- Backups of EasyAuth, FLP records and world/player data. Read [UUID implications](../accounts/uuid.md) before enabling bindings.

## 1. Install plugins and mods

Stop the proxy and backend, then install:

| Location | Files |
| --- | --- |
| Velocity `plugins/` | FLP **Velocity** edition |
| Fabric `mods/` | FLP **Fabric 26.2** edition, Fabric API, FabricProxy-Lite and EasyAuth |

Do not install FLP's Bukkit/Folia edition on Fabric or install the conflicting FabricProxy mod alongside FabricProxy-Lite.

## 2. Configure forwarding

In the proxy's `velocity.toml`, set:

```toml
online-mode = false
player-info-forwarding-mode = "modern"
```

Set `online-mode=false` in the Fabric backend's `server.properties`. Start the proxy and backend once to generate configuration files, then stop both. In the backend's `config/FabricProxy-Lite.toml`, set `secret` to the contents of the proxy's `forwarding.secret` file. Keep this secret private.

## 3. Configure EasyAuth

In the backend's EasyAuth 3.4.4 `config/EasyAuth/main.conf`, set:

| Option | Required value |
| --- | --- |
| `premium-auto-login` | `false` |
| `offline-by-default` | `false` |
| `floodgate-auto-login` | `false` |
| `session-timeout` | `-1` |
| `enable-global-password` | Prefer `false`; if enabled, `single-use-global-password` must be `true` |

In `config/EasyAuth/extended.conf`, set:

| Option | Required value |
| --- | --- |
| `skip-all-auth-checks` | `false` |
| `allow-all-packets` | `false` |

Edit EasyAuth's generated files; do not add these options to FLP's YAML file.

## 4. Configure FLP

1. Copy the UUID from the proxy's `plugins/fastloginplus/proxyId.txt`.
2. Set `allowed-proxy-ids` in the backend's `config/fastloginplus-fabric.yml` to that UUID. You can also set `language: en` or `language: zh`.
3. Configure Java/Bedrock login policies in the proxy's `plugins/fastloginplus/config.yml`. Do not add proxy policies to the Fabric configuration.

The proxy ID and forwarding secret are different values and cannot be exchanged. Restart after changing configuration.

## 5. Start and check

Use a firewall to prevent public access to the backend. Start the proxy and backend, check the startup logs, and try premium and password login with test accounts.

## Expected behavior

A verified premium player can log in without a password when proxy policy and the EasyAuth account allow it. An unbound password account still requires its password. Its owner can log in, use `/flp premium`, follow the confirmation prompts and reconnect manually. The original password is retained after conversion.

`premiumUuid: false` can keep an offline connection UUID while the verified Mojang UUID still identifies the EasyAuth premium owner. This does not migrate player data. A successful name-follow operation must match the verified account identity and cannot overwrite another name's account.

Bedrock support is deliberately narrower: an unlinked signed Floodgate connection may auto-login to an **existing completed** EasyAuth account with the same identity and a stored credential. New Bedrock account creation, linked login and premium conversion are not supported by this path. See [Bedrock integration](../integrations/bedrock.md).

Changing backend FLP settings requires a restart. `/flp reload` and hot reload are unsupported. Console commands that must be forwarded to the proxy require an online player on the backend; they fail if the server is empty.

Platform setup references: [Velocity forwarding](https://docs.papermc.io/velocity/player-information-forwarding/), [FabricProxy-Lite](https://github.com/OKTW-Network/FabricProxy-Lite) and [EasyAuth 3.4.4 configuration source](https://github.com/NikitaCartes/EasyAuth/tree/3.4.4/src/main/java/xyz/nikitacartes/easyauth/config). Do not substitute EasyAuth 4.x instructions for this version's policy/API checks.
