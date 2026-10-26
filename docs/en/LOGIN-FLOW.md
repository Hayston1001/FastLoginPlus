# Login flow and identity trust

This reference describes the forthcoming 1.0.0 implementation. For administrator procedures, use [login methods](accounts/login.md), [binding](accounts/binding.md) and [deployment guides](overview/requirements.md). A historical call sequence or a class name does not replace the account decision described here.

## 1. Select and verify the connection

For classic Java entry, `JoinManagement` routes by the authoritative FLP record. A name with consent, or a premium UUID observation without active same-name cracked fallback, requests a new premium handshake. Known cracked names take the password route. New names can invoke the Mojang name lookup when `autoRegister`, `nameChangeCheck` or `offline-whitelist` needs it.

Unknown name lookup returns a placeholder profile with `rowId=-1`; `loadProfile(name)==null` indicates a read failure, not an unknown player. An incomplete name lookup is refused with `auth-server-unreachable`, not treated as a definitive offline name. Per-name locks protect applicable load/modify/save windows; they are not global cross-process transactions.

Standalone ProtocolLib interception performs the online-mode handshake and verifies the challenge through the session service. On a proxy the proxy's native online-mode handshake establishes identity. A successful name lookup is never sufficient to mark a session verified. `premiumUuid` selects the forwarded/delivered UUID after verification and does not decide whether verification runs.

Connection limits run at standalone entry or proxy pre-login. Limit hits block; there is no `anti-bot.action`/Ignore route. Floodgate identity follows its own verified Bedrock path rather than becoming a Java identity because of its name.

## 2. Deliver facts across the proxy boundary

The proxy owns verification, network admission, policy declarations and FLP records, and performs proxy-side persistence/forwarding. The backend owns local auth-account checks, the final auth-plugin action decision and execution. Both sides make decisions and perform work.

- Modern Velocity forwarding authenticates the forwarded profile with its forwarding secret. The trust is in the proxy process, not an isolated trusted plugin inside an otherwise hostile proxy.
- Legacy forwarding is unsigned. Current identity declarations need their own verified signature and connection association before FLP grants premium admission. A raw v4 UUID or UUID property is only a candidate value, never authentication by shape.
- Backend trust lists check FLP proxy IDs. Classic backend-to-proxy toggle/delete/success messages echo the backend's allowed proxy set when checking is enabled. This is not an independent identity for every backend plugin, nor protection against a compromised trusted process.
- Claims must be validated for origin, profile/name/UUID association, freshness and applicable duplicate rules. Missing, conflicting or unreadable identity must not turn into permission.

The `fastloginplus:force` channel remains the classic account-action transport. Forwarded identity/policy facts allow early account decisions before a join-time message can arrive. Backend ProtocolLib interception is not registered in proxy mode. Correct forwarding and network isolation remain mandatory even when FLP signs its own claims.

## 3. Decide the local account action

Classic declared-facts decisions are represented by `LoginMethodDecider`. With auto-login enabled:

| Local account | Verified Java connection | Unverified connection |
| --- | --- | --- |
| None | Auto-register/login if permitted; otherwise guide registration | Guide normal registration |
| Password | Consent allows auto-login; without it keep password | Consent conflict rejects; otherwise keep password |
| Premium-owned | Allow the matching verified-owner recovery path | Reject |
| Read failure | Keep authentication locked | Keep authentication locked |

Concrete AuthMe early paths additionally check UUID consistency, corruption and safe placeholder handling. Generic standalone hooks keep their platform/session-specific force action path; do not assume the declared-facts table proves identical early checks in every plugin. With `autoLogin` off, no FLP passwordless action is granted; policy-independent ownership rejection still protects premium-owned accounts.

The proxy's declared policy is authoritative; a backend's same-named setting must not override it. Classic old-peer compatibility can consult local policy if no declaration block was provided, unless strict requirements reject it. This is an upgrade boundary, not a reason to fabricate missing identity.

AuthMe's early configuration/pre-login integration can act before dialogs block. A join-time `ForceLoginTask` is not the only path, and a task being sent is not proof of successful authentication. Check action results or independently confirmed already-authenticated state before acknowledging success.

## 4. Persist only the appropriate result

`UUID` records a premium identity observation; `Premium` records consent/confirmed premium outcome; `SameNameCracked` records the configured failure observation. The normal proxy verification observation must not itself grant account consent before the backend decision. Classic confirmed outcomes can be acknowledged to the proxy; explicit authorized list commands are another consent source.

A proxied classic backend session has no local FLP profile to write. It still reads/writes the auth plugin's local accounts. A successful premium-owned account recovery can reconcile disagreement with FLP consent; do not revive the old claim that every lost acknowledgement permanently requires an operator or a cracked client.

Offline bookkeeping must not erase a premium observation/consent incidentally. Explicit cracked/delete operations have different, authorized semantics. Same-name fallback is persistent and per name when enabled; an outage is not automatically a definitive failure. See [list rules and proxy attribution limits](accounts/lists.md).

## 5. Fabric and Bedrock differences

Fabric receives modern-forwarded `IdentityClaim` facts, validates the actual profile and reads EasyAuth state. A bound account must match the verified UUID; a password account requires consent to convert. Conversion retains its password. Unverified or inconsistent bound access rejects. Local SQL read failures are not open accounts in the supported SQLite path.

Self binding sends the shared toggle request after password authentication. Proxy confirmation, consent persistence and a later manual verified reconnect form the current chain. There is no transfer, cookie or ten-minute local binding session. `kick-toggle` determines kick versus keeping the current connection, not automatic reconnection.

Bedrock claims are signed separately. Classic AuthMe pre-login can create/login supported accounts under proxy policy. Fabric only supports fresh signed unlinked identities with existing completed matching EasyAuth accounts; no new/linked account registration or Java premium conversion. Process-local replay tracking does not imply restart-persistent replay storage.

## Review and test boundaries

Test disconnect, reconnect, late/stale callbacks, duplicate messages, changed policy, account/SQL errors, reload/shutdown and failed persistence. Verify local and proxy outcomes separately; there is no cross-database transaction. Keep unit tests, compilation, server startup, real clients and fault injection as distinct evidence.

Source entry points: [core routing/decision](https://github.com/Hayston1001/FastLoginPlus/tree/main/core/src/main/java/com/github/games647/fastlogin/core/shared), [backend integration](https://github.com/Hayston1001/FastLoginPlus/tree/main/bukkit/src/main/java/com/github/games647/fastlogin/bukkit), [protocol](https://github.com/Hayston1001/FastLoginPlus/tree/main/protocol), [Fabric](https://github.com/Hayston1001/FastLoginPlus/tree/main/fabric/mc26_2).
