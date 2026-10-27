# Velocity configuration

File: `plugins/fastloginplus/config.yml` on the Velocity proxy. Stop the proxy to edit it, then restart. This file controls network login policies and the FLP list; backend authentication accounts and passwords remain on the backend.

## General and login

| Option | Default | Purpose |
| --- | --- | --- |
| `language` | `en` | Proxy message language; `en` and `zh` are built in, custom files are supported |
| `debug` | `false` | Enable diagnostic logs |
| `check-update` | `true` | Check for releases, without installing them |
| `update-check-interval` | `24` | Update interval in hours |
| `autoRegister` | `true` | Select eligible new names for premium verification, not a master registration switch |
| `autoLogin` | `true` | Allow eligible verified players to log in without a password |
| `auto-register-unknown` | `true` | Allow creating a local account for a verified Java player who lacks one |
| `premiumUuid` | `true` | Use the premium UUID; `false` uses the offline UUID. Back up player data before changing |
| `nameChangeCheck` | `true` | Check premium name changes and update FLP records, without moving player data |
| `secondAttemptCracked` | `false` | Allow a later password route after an eligible verification failure |
| `offline-whitelist` | `false` | Reject offline Java names unknown to FLP |
| `forwardSkin` | `true` | Forward verified players' skins and capes to the backend |
| `premium-warning` | `true` | Ask players to repeat self-binding commands for confirmation |
| `kick-toggle` | `true` | Disconnect after applicable account switching; players reconnect manually |
| `verify-backend-messages` | `true` | Check backend account-management messages; disable only temporarily for a controlled upgrade |

## Connection limits

Limits reject connections and do not replace a firewall. Check real IP forwarding before changing them.

| Option | Default | Purpose |
| --- | --- | --- |
| `anti-bot.enabled` | `true` | Enable connection limits |
| `anti-bot.connections` | `600` | Global connections per window |
| `anti-bot.expire` | `10` | Global window in minutes |
| `anti-bot.per-ip-connections` | `20` | Connections per IP in the long window |
| `anti-bot.per-ip-expire` | `5` | Per-IP long window in minutes |
| `anti-bot.burst-limit` | `10` | Connections per IP in the short window |
| `anti-bot.burst-window` | `10` | Short window in seconds |
| `anti-bot.ban-duration` | `5` | Temporary IP ban in minutes |
| `anti-bot.trusted-ips` | Empty | IP addresses exempt from FLP connection limits |

## Mojang and Floodgate

| Option | Default | Purpose |
| --- | --- | --- |
| `mojang-retry-count` | `3` | Total name-lookup attempts; does not control native proxy session verification |
| `mojang-retry-delay` | `500` | Base name-lookup retry delay in milliseconds |
| `ip-addresses` | Empty | Local outbound request addresses |
| `mojang-request-limit` | `600` | Direct name lookups per 10 minutes, 0 to 600; 0 requires an HTTP proxy |
| `proxies` | Empty | HTTP proxies for Mojang requests, such as `host:port` |
| `autoLoginFloodgate` | `false` | Allow supported Floodgate automatic login |
| `autoRegisterFloodgate` | `false` | Allow Floodgate registration; automatic login must also be allowed |
| `allowFloodgateNameConflict` | `false` | Permit eligible unlinked Bedrock names to conflict with premium Java names |

`autoLoginFloodgate` and `autoRegisterFloodgate` accept `false`, `true`, `linked` or `no-conflict`. Fabric has narrower Bedrock support; see [Geyser and Floodgate](../integrations/bedrock.md).

## FLP database

This database stores the proxy's FLP list, not backend authentication accounts. Back up and plan migration before switching; changing configuration does not migrate SQLite data.

| Option | Default | Purpose |
| --- | --- | --- |
| `driver` | `sqlite` | `sqlite` or `mysql` |
| `database` | `{pluginDir}/FastLogin.db` | SQLite file path, or MySQL database name |
| `host` | `127.0.0.1` | MySQL host |
| `port` | `3306` | MySQL port |
| `username` | `myUser` | MySQL username; change before use |
| `password` | `myPassword` | MySQL password; change and keep private |
| `timeout` | `30` | Connection acquisition timeout in seconds |
| `lifetime` | `1800` | Maximum connection lifetime in seconds; 0 is unlimited, positive values below 300 are raised to 300 |
| `useSSL` | `false` | Enable the MySQL SSL path |
| `sslMode` | `Required` | Driver mode for the SSL path |
| `allowPublicKeyRetrieval` | `false` | MySQL public-key retrieval option on the SSL path |
| `ServerRSAPublicKeyFile` | Empty | Optional local RSA public-key file |

See [database configuration](database.md) for driver and connection details. Velocity bundles SQLite and MariaDB drivers.
