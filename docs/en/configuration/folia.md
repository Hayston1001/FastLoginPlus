# Folia configuration

File: `plugins/fastloginplus/config.yml` on Folia. Stop the server to edit it, then restart. Folia and Bukkit use the same configuration template, with the same option defaults and standalone/proxy-backend scope.

For Folia account commands, use the `fastloginplus.folia.command.*` permission prefix instead of the Bukkit prefix.

Behind a proxy, the proxy controls login policies, lists, connection limits and the FLP database. `respectIpLimit` and local settings such as language still apply on the backend. See [Velocity](velocity.md) or [BungeeCord](bungeecord.md).

## General and login

| Option | Default | Purpose |
| --- | --- | --- |
| `language` | `en` | Local message language; `en`, `zh` and custom files are supported |
| `debug` | `false` | Enable diagnostic logs |
| `check-update` | `true` | Check releases without installing them |
| `update-check-interval` | `24` | Check interval in hours |
| `autoRegister` | `true` | Standalone: select eligible new names for premium verification |
| `autoLogin` | `true` | Standalone: allow eligible players to log in without a password |
| `auto-register-unknown` | `true` | Standalone: allow creating a local account for a verified Java player who lacks one |
| `premiumUuid` | `true` | Standalone: use premium UUID; `false` uses offline UUID |
| `nameChangeCheck` | `true` | Standalone: check premium name changes without moving player data |
| `secondAttemptCracked` | `false` | Standalone: allow a later password route after an eligible verification failure |
| `offline-whitelist` | `false` | Standalone: reject offline Java names unknown to FLP |
| `forwardSkin` | `true` | Standalone: provide verified players' skins and capes |
| `premium-warning` | `true` | Standalone: require confirmation for self-binding |
| `kick-toggle` | `true` | Disconnect after standalone switching; also relevant to queued proxy-backend toggles |
| `require-declared-proxy-policy` | `false` | Proxy backend: require proxy login policies; enable after upgrading the proxy |
| `respectIpLimit` | `false` | Honor local AuthMe registration limits per IP |
| `useProxyAgnosticResolver` | `true` | Standalone: omit IP during session verification |
| `verifyClientKeys` | `false` | Standalone: check applicable client public-key signatures |

## Connection limits

These apply locally only on standalone servers; in proxy deployments configure the proxy. Limits reject connections and do not replace a firewall.

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
| `mojang-retry-count` | `3` | Standalone: total attempts per request |
| `mojang-retry-delay` | `500` | Standalone: base retry delay in milliseconds |
| `ip-addresses` | Empty | Local outbound request addresses |
| `mojang-request-limit` | `600` | Direct name lookups per 10 minutes; 0 requires an HTTP proxy |
| `proxies` | Empty | HTTP proxies for Mojang requests, such as `host:port` |
| `autoLoginFloodgate` | `false` | Standalone: allow supported Floodgate automatic login |
| `autoRegisterFloodgate` | `false` | Standalone: allow automatic registration; automatic login must also be allowed |
| `allowFloodgateNameConflict` | `false` | Standalone: permit eligible unlinked Bedrock name conflicts |

Both automatic Floodgate options accept `false`, `true`, `linked` or `no-conflict`; see [Geyser and Floodgate](../integrations/bedrock.md). The proxy controls Floodgate policy behind a proxy; `ip-addresses`, `mojang-request-limit` and `proxies` can still apply to relevant Paper lookup paths.

## FLP database

These settings control the standalone FLP list; authentication accounts use a separate database.

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

Back up and plan migration before switching; configuration changes do not migrate SQLite data. See [database configuration](database.md).
