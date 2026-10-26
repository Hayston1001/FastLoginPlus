# Verify and secure the installation

Use a staging copy and disposable accounts. Do not test destructive account commands on an unbacked-up production account.

## Startup and network checks

- Confirm the exact FLP build/commit, correct platform artifact, auth plugin version and Java version on every side.
- Confirm the auth hook, database and configured language loaded without errors. Fabric should report EasyAuth policy/storage compatibility.
- Proxy backend: verify forwarding mode, matching forwarding secret where applicable, and the correct trusted FLP proxy ID after a backend restart.
- Attempt to reach a backend from outside the trusted network. It must be blocked or reject unauthenticated forwarding. An allowlist file is not enough.
- Standalone: verify packet-library compatibility, particularly encryption-response interception. Enable debug temporarily if needed.

## Client test matrix

| Scenario | What to check |
| --- | --- |
| Fresh verified Java owner, no local account | Auto-register/login only if policy allows; otherwise normal registration |
| Existing password account | No unintended passwordless takeover; authenticated owner can request an upgrade |
| Returning bound account | Correct verified owner succeeds; unverified or conflicting identity does not bypass authentication |
| `autoLogin: false` | No FLP passwordless login; retain the auth plugin flow and account safety |
| `auto-register-unknown: false` | No automatic creation of the missing local Java account |
| Unknown offline name with whitelist enabled | Rejected; a known cracked record still requires its password |
| Disconnect during verification or a command | No false success or access to another connection; inspect durable state before retrying |
| Manual reconnect after a toggle | Desired route and UUID apply; no automatic reconnect is expected |
| Backend/proxy restart | Trust configuration and account changes survive as intended |

For Floodgate, test the configured linked/name-conflict policy. On Fabric, test only the documented existing-account path and confirm new/linked accounts are not silently admitted. On Folia, inspect scheduler/thread errors as well as player-visible results.

## Failure checks

In staging, test unavailable Mojang services, an unreadable auth/FLP database, missing trust and a failed account write. A read error is not an empty account and an unverified connection must not gain passwordless access. Observe both player feedback and database contents after reconnect/restart. Restore normal connectivity/settings after testing.

Keep evidence separate: compiling a JAR, starting a server, logging in with a real client and injecting a failure prove different things. This manual is source-checked documentation, not a claim that every combination has passed this matrix.
