# Bukkit configuration

File: `plugins/fastloginplus/config.yml` on Spigot/Paper. Stop the server to edit it, then restart. The same file exists behind a proxy, but options marked **standalone** are controlled on the proxy in that deployment.

## General and login

| Option | Default | Purpose |
| --- | --- | --- |
| `language` | `en` | Message language; `en` and `zh` are built in, custom files are supported |
| `debug` | `false` | Enable diagnostic logs |
| `check-update` | `true` | Check for releases, without installing them |
| `update-check-interval` | `24` | Update interval in hours |
| `autoRegister` | `true` | Standalone: select eligible new names for premium verification, not a master registration switch |
| `autoLogin` | `true` | Standalone: allow eligible verified players to log in without a password |
| `auto-register-unknown` | `true` | Standalone: allow creating a local account for a verified Java player who lacks one |
| `premiumUuid` | `true` | Standalone: use the premium UUID; `false` uses the offline UUID. Back up player data before changing |
| `nameChangeCheck` | `true` | Standalone: check premium name changes and update FLP records, without moving player data |
| `secondAttemptCracked` | `false` | Standalone: allow a later password route after an eligible verification failure |
| `offline-whitelist` | `false` | Standalone: reject offline Java names unknown to FLP |
| `forwardSkin` | `true` | Standalone: provide verified players' skins and capes |
| `premium-warning` | `true` | Standalone: ask players to repeat self-binding commands for confirmation |
| `kick-toggle` | `true` | Disconnect after standalone switching for manual reconnect; also relevant to queued proxy-backend toggles |
| `require-declared-proxy-policy` | `false` | Proxy backend: require login policies from the proxy; enable after upgrading and confirming proxy support |
| `respectIpLimit` | `false` | Honor AuthMe's registration limit per IP, including behind a proxy |
| `useProxyAgnosticResolver` | `true` | Standalone: omit the IP during session verification, useful for DNAT |
| `verifyClientKeys` | `false` | Standalone: check applicable client public-key signatures; not a replacement for premium verification |

## Connection limits

`anti-bot` applies only on standalone servers; configure it on the proxy when using one. Limits reject connections and do not replace a firewall.

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
| `ip-addresses` | Empty | Local outbound request addresses; on proxy backends only used for some Paper lookups |
| `mojang-request-limit` | `600` | Direct name lookups per 10 minutes, 0 to 600; 0 requires an HTTP proxy |
| `proxies` | Empty | HTTP proxies for Mojang requests, such as `host:port` |
| `autoLoginFloodgate` | `false` | Standalone: allow supported Floodgate automatic login |
| `autoRegisterFloodgate` | `false` | Standalone: allow Floodgate registration; automatic login must also be allowed |
| `allowFloodgateNameConflict` | `false` | Standalone: permit eligible unlinked Bedrock names to conflict with premium Java names |

`autoLoginFloodgate` and `autoRegisterFloodgate` accept `false`, `true`, `linked` or `no-conflict`; see [Geyser and Floodgate](../integrations/bedrock.md). Proxy backends receive Floodgate policy from the proxy. On a proxy backend, `ip-addresses`, `mojang-request-limit` and `proxies` only apply to relevant Paper lookup paths.

## FLP database

These settings control the standalone FLP list; proxy deployments use the proxy database. The authentication plugin has a separate account database.

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

Back up and plan migration before changing databases; configuration changes do not migrate SQLite data. See [database configuration](database.md) for driver and connection details.
