# Files, defaults and scope

## Files you edit

| Deployment/file | Purpose |
| --- | --- |
| Classic `plugins/fastloginplus/config.yml` | Local settings; proxy or backend template selected automatically |
| Classic `plugins/fastloginplus/messages_<language>.yml` | Player/operator message overrides |
| Classic backend `allowed-proxies.txt` | Trusted FLP proxy UUIDs, one per line; restart to apply |
| Velocity `proxyId.txt` | Generated FLP proxy identity; preserve it |
| BungeeCord's own `config.yml` `stats` | Bungee instance identity used by FLP |
| Fabric `config/fastloginplus-fabric.yml` | Backend language, debug, update checks and `allowed-proxy-ids` |
| Fabric `config/fastloginplus-fabric/messages_en.yml` / `messages_zh.yml` | Optional localized overrides |
| Classic backend `pending-relay.json` | Durable pending requests, not a policy file or a file to casually delete |

Paths refer to each process's own working directory. Use the actual generated plugin folder. Forwarding secrets belong to the proxy/server or FabricProxy-Lite configuration, not to FLP's allowlist.

## Template refresh and restart

Classic platforms generate the on-disk `config.yml` from either the Bukkit/Folia backend template or BungeeCord/Velocity proxy template. Startup refresh preserves existing values while restoring template structure/comments and supplying missing options; it is not a reset to new defaults. Options outside the chosen template can be dropped. Review a copied config rather than assuming proxy/backend files are interchangeable.

Fabric uses its own flat YAML template. The old `.properties` instructions do not apply. Unsupported YAML is diagnosed; do not put nested proxy policy sections in this file.

Edit while stopped and restart the affected process. No general `/flp reload` command is provided. Fabric freezes its backend-local settings at startup; checking EasyAuth's in-memory policy on each operation does not reload either plugin's files.

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
| `language` | `en` | Classic custom language code; Fabric supports `en`/`zh` |
| `debug` | `false` | This process's diagnostic logs |
| `check-update` | `true` | This process's background release checks |
| `update-check-interval` | `24` | Hours; Fabric invalid/below-one values fall back to 24 |
| Fabric `allowed-proxy-ids` | Empty string | Backend trusted proxy UUIDs, comma-separated or YAML list |

Empty trust never enables an identity-dependent action. Invalid IDs must be repaired, not replaced with an "allow all" value. Keep `verify-backend-messages` enabled on proxies and review `require-declared-proxy-policy` before upgrading old peers.

Remaining keys: [Java policies](java.md), [Bedrock policies](bedrock.md), [network and anti-bot](network.md), [database](database.md). Message-file behavior is documented under [languages](../operations/languages.md).
