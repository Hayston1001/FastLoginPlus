# Java verification and login policies

Defaults below come from the Bukkit/Folia and proxy templates. In a proxy network, configure Java login policies on the **proxy**; do not add these options to the Fabric backend configuration.

| Option | Default | Meaning and limits |
| --- | --- | --- |
| `autoRegister` | `true` | Select eligible new premium names for verification/registration routing. Name-change and whitelist routes are independent; not a universal registration veto |
| `autoLogin` | `true` | Permit passwordless actions for verified Java identities when account ownership allows. Off prevents automatic login/claiming/registration |
| `auto-register-unknown` | `true` | Create a missing local account for a verified Java identity. Off leaves registration to the auth plugin. Never creates an account merely for an unknown offline name |
| `premiumUuid` | `true` | Deliver the verified Mojang UUID; false keeps a name-derived offline connection UUID. Does not disable identity verification |
| `nameChangeCheck` | `true` | Look up new names and reconcile existing FLP records by UUID. Not a world-data migration |
| `secondAttemptCracked` | `false` | After a failed verification, allow eligible players with the same name to use password login on a later attempt; see [lists and admission](../accounts/lists.md) |
| `offline-whitelist` | `false` | Deny unknown offline Java names using FLP's known list, independently of password/account creation |
| `forwardSkin` | `true` | Forward premium texture properties, including cape data; supported custom SkinsRestorer skins take priority |
| `premium-warning` | `true` | Ask a player requesting their own premium binding to repeat the command; administrators acting on others are not prompted |
| `kick-toggle` | `true` | Disconnect players after applicable account changes; they must reconnect manually. `false` keeps the current connection |
| `respectIpLimit` | `false` | Backend AuthMe registration IP-limit check, including behind a proxy; not a generic feature of every auth hook |
| `verifyClientKeys` | `false` | Standalone ProtocolLib client public-key signature verification for applicable clients. Not Mojang session verification and not a proxy option |
| `useProxyAgnosticResolver` | `true` | Standalone session verification omits the IP parameter for DNAT-like setups. Not included in the proxy template; native proxy session verification is separate |
| `require-declared-proxy-policy` | `false` | Require the proxy to provide login and registration policies to a Bukkit/Folia backend. Enable after upgrading the proxy and confirming support |
| `verify-backend-messages` | `true` | Have the proxy check account-management messages sent by backends. Disable only temporarily for a controlled upgrade from older versions |

## Examples of intent

- To verify a Java owner but keep existing offline player data, review `premiumUuid: false`; do not disable verification.
- To keep password/registration flows instead of FLP passwordless actions, use `autoLogin: false` and assess already converted passwordless accounts first.
- To require a verified player with no local account to choose a password, set `auto-register-unknown: false`; this does not permit an unverified player into a bound premium account.
- To restrict new offline admission, use `offline-whitelist: true` and manage FLP lists. An auth database row alone is not admission.

Proxy policies take precedence over same-named backend settings. Mixed older versions may use backend-local or default policies; upgrade both sides and check login before requiring declared proxy policies.

See [login methods](../accounts/login.md), [list semantics](../accounts/lists.md) and [UUID risks](../accounts/uuid.md).
