# Login methods

## Verification is not automatic account access

A name lookup asks whether Mojang knows a Java account with this name. The login handshake proves whether **this connection** owns that identity. Neither query alone decides whether an existing local password account should be opened.

On proxy networks, the proxy verifies identity, controls admission/forwarding and owns FLP records. The backend combines trusted identity and policy facts with the auth plugin's local account state, then chooses and executes the account action. On standalone servers, these responsibilities live on the same server; generic standalone auth hooks retain their own force-login behavior, while AuthMe has additional early ownership checks.

## Common results

| Identity and local account | Expected route |
| --- | --- |
| Verified Java identity, no account | Automatic creation/login if `autoLogin` and `auto-register-unknown` allow; otherwise registration |
| Verified Java identity, existing password account without upgrade consent | Password authentication, not ownership by name alone |
| Verified identity with permitted consent or a matching premium-owned account | Passwordless login, subject to policy and account consistency |
| Unverified connection to a normal password account | Password login |
| Unverified/conflicting connection to a premium-owned account | Reject rather than unlock |
| Account state cannot be read | Keep authentication locked or reject; never interpret the error as permission to create/open an account |

The exact account capabilities differ between [AuthMe](../integrations/authme.md), [EasyAuth](../integrations/easyauth.md) and [other hooks](../integrations/auth-plugins.md). With `autoLogin: false`, FLP does not grant passwordless access, including to a verified owner; a converted account without a usable password may require operator recovery. Do not promise a password dialog can recover every account.

## Registration controls are different

- `autoRegister` participates in selecting new Java names for verification. It is not a master switch for every possible registration route.
- `auto-register-unknown` controls automatic local-account creation for an already verified Java identity with no local account.
- `autoLogin` controls passwordless account actions.
- `offline-whitelist` controls admission based on FLP's known list; it does not authenticate or register a password account.
- Floodgate has independent identity and auto-auth policies.

For example, disabling `autoRegister` does not disable a verification route requested by a returning premium record, name-change handling or admission policy. Read the [configuration reference](../configuration/java.md) before combining switches.

## Failures and retrying

An incomplete Mojang name lookup is not a definitive offline identity. FLP refuses the uncertain lookup instead of creating a new password account from that failure. A failed session check, a service outage and a disconnected client are also different situations. Proxy failure attribution has limits; see [fallback semantics](lists.md).

The messages "request forwarded", "identity verified" and "account logged in" describe different milestones. Only a confirmed account action is a successful passwordless login. Never remove account protection just to silence a failure message.
