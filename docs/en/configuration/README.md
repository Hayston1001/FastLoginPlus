# Files, defaults and scope

## Files you edit

| Deployment/file | Purpose |
| --- | --- |
| Bukkit/Folia/BungeeCord/Velocity `plugins/fastloginplus/config.yml` | Local settings; proxy or backend template selected automatically |
| Bukkit/Folia/BungeeCord/Velocity `plugins/fastloginplus/messages_<language>.yml` | Player and operator messages |
| Bukkit/Folia backend `plugins/fastloginplus/allowed-proxies.txt` | Trusted FLP proxy UUIDs, one per line; restart to apply |
| Velocity `proxyId.txt` | Generated FLP proxy identity; preserve it |
| BungeeCord's own `config.yml` `stats` | Bungee instance identity used by FLP |
| Fabric `config/fastloginplus-fabric.yml` | Backend language, debug, update checks and `allowed-proxy-ids` |
| Fabric `config/fastloginplus-fabric/messages_en.yml` / `messages_zh.yml` | Optional localized overrides |
| Bukkit/Folia backend `plugins/fastloginplus/pending-relay.json` | Requests waiting to be sent to the proxy, not a configuration file; do not delete casually |

Paths refer to each process's own working directory. Use the actual generated plugin folder. Forwarding secrets belong to the proxy/server or FabricProxy-Lite configuration, not to FLP's allowlist.

## Template refresh and restart

Bukkit/Folia and BungeeCord/Velocity use separate backend and proxy configuration templates. Startup supplies missing options and preserves existing values; options outside the relevant template may be removed. Proxy and backend configuration files are not interchangeable.

Fabric uses its own flat YAML template. The old `.properties` instructions do not apply. Unsupported YAML is diagnosed; do not put nested proxy policy sections in this file.

Edit while stopped, then restart the affected proxy or server. FLP has no general `/flp reload` command.

## Who owns each setting?

| Setting group | Standalone | Proxy deployment |
| --- | --- | --- |
| Java route, FLP lists, admission, connection limits | This server | Proxy |
| `autoLogin`, `auto-register-unknown` | This server | Proxy declares; backend decides account action and enforces it |
| `premiumUuid`, `forwardSkin` | This server | Proxy selects forwarded profile |
| Auth account/password and auth-plugin limits | Backend | Backend |
| Language, debug, update checks | Each process | Each process independently |
| Trusted proxy IDs | Not needed for a direct server | Each backend |

Some compatibility paths differ: `respectIpLimit` is an AuthMe backend check even behind a proxy; Paper's configure path can still use backend name-lookup settings. See the per-option references rather than inferring scope from a key existing in both templates.

## Local/general defaults

| Option | Default | Scope |
| --- | --- | --- |
| `language` | `en` | Bukkit/Folia/BungeeCord/Velocity support custom language codes; Fabric supports `en`/`zh` |
| `debug` | `false` | This process's diagnostic logs |
| `check-update` | `true` | This process's background release checks |
| `update-check-interval` | `24` | Hours; Fabric invalid/below-one values fall back to 24 |
| Fabric `allowed-proxy-ids` | Empty string | Backend trusted proxy UUIDs, comma-separated or YAML list |

Empty trust never enables an identity-dependent action. Invalid IDs must be repaired, not replaced with an "allow all" value. Keep `verify-backend-messages` enabled on proxies and review `require-declared-proxy-policy` before upgrading old peers.

Remaining keys: [Java policies](java.md), [Bedrock policies](bedrock.md), [network and anti-bot](network.md), [database](database.md). Message-file behavior is documented under [languages](../operations/languages.md).
