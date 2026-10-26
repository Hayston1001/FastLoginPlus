# Java verification and login policies

Defaults below come from the current classic templates. Configure routing and declared policies on the **proxy** in a proxy network, not on its backends. Fabric receives those facts; it does not define these options in its local YAML.

| Option | Default | Meaning and limits |
| --- | --- | --- |
| `autoRegister` | `true` | Select eligible new premium names for verification/registration routing. Name-change and whitelist routes are independent; not a universal registration veto |
| `autoLogin` | `true` | Permit passwordless actions for verified Java identities when account ownership allows. Off prevents automatic login/claiming/registration |
| `auto-register-unknown` | `true` | Create a missing local account for a verified Java identity. Off leaves registration to the auth plugin. Never creates an account merely for an unknown offline name |
| `premiumUuid` | `true` | Deliver the verified Mojang UUID; false keeps a name-derived offline connection UUID. Does not disable identity verification |
| `nameChangeCheck` | `true` | Look up new names and reconcile existing FLP records by UUID. Not a world-data migration |
| `secondAttemptCracked` | `false` | Enable persistent same-name offline fallback for unconsented names after a settled failure |
| `offline-whitelist` | `false` | Deny unknown offline Java names using FLP's known list, independently of password/account creation |
| `forwardSkin` | `true` | Forward premium texture properties, including cape data; supported custom SkinsRestorer skins take priority |
| `premium-warning` | `true` | Ask a self-invoking player to repeat a premium request for confirmation. Proxy toggle requests, including current Fabric self requests, use the proxy gate; other-player admin requests do not |
| `kick-toggle` | `true` | Disconnect after applicable state changes so a manual reconnect applies the route. False keeps the session; no transfer or automatic reconnect |
| `respectIpLimit` | `false` | Backend AuthMe registration IP-limit check, including behind a proxy; not a generic feature of every auth hook |
| `verifyClientKeys` | `false` | Standalone ProtocolLib client public-key signature verification for applicable clients. Not Mojang session verification and not a proxy option |
| `useProxyAgnosticResolver` | `true` | Standalone session verification omits the IP parameter for DNAT-like setups. Not included in the proxy template; native proxy session verification is separate |
| `require-declared-proxy-policy` | `false` | Classic backend compatibility switch: require policy facts before accepting force-login/register messages. Enable only after proxy support is upgraded; not a replacement for authentication/allowlisting |
| `verify-backend-messages` | `true` | Proxy-only checking of backend toggle/delete/success messages against the echoed trusted proxy set. Disable only temporarily for a controlled old-peer migration |

## Examples of intent

- To verify a Java owner but keep existing offline player data, review `premiumUuid: false`; do not disable verification.
- To keep password/registration flows instead of FLP passwordless actions, use `autoLogin: false` and assess already converted passwordless accounts first.
- To require a verified player with no local account to choose a password, set `auto-register-unknown: false`; this does not permit an unverified player into a bound premium account.
- To restrict new offline admission, use `offline-whitelist: true` and manage FLP lists. An auth database row alone is not admission.

Proxy declarations override backend copies. The classic old-peer compatibility path can consult local/default policy when no block was declared; it is transitional, not the recommended 1.0.0 arrangement. A missing or invalid identity proof must never be replaced with a name/UUID guess. Upgrade both sides and test before tightening compatibility switches.

See [login methods](../accounts/login.md), [list semantics](../accounts/lists.md) and [UUID risks](../accounts/uuid.md).
