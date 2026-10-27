# Bedrock policies

Set these options in the standalone server's FLP configuration, or on the **proxy** in a proxy network.

| Option | Default | Purpose |
| --- | --- | --- |
| `autoLoginFloodgate` | `false` | Permit supported Floodgate passwordless login |
| `autoRegisterFloodgate` | `false` | Permit supported Floodgate account creation; requires permitted auto-login |
| `allowFloodgateNameConflict` | `false` | Allow an unlinked Bedrock name to overlap a Java premium name under the applicable path's conflict rules |

For the auto-auth options, use:

| Value | Meaning |
| --- | --- |
| `false` | Do not grant this automatic action |
| `true` | Permit it for supported linked/unlinked identities |
| `linked` | Permit only identities linked by Floodgate to a Java account |
| `no-conflict` | Permit subject to the applicable Java-name conflict check |

`allowFloodgateNameConflict` should be treated as the boolean conflict switch, not as another auto-login selector. Keeping a non-conflicting Floodgate prefix is strongly recommended. Do not remove prefixes simply to reuse Java accounts.

## Spigot, Paper and Folia

With supported AuthMe versions, FLP can handle a Floodgate account before the login dialog appears. Automatic account creation requires both auto-registration and auto-login. Support varies by authentication plugin; see the [integration guide](../integrations/bedrock.md).

The Java `auto-register-unknown` setting does not replace `autoRegisterFloodgate`. Admission, account creation, linked identity and name conflict are separate checks.

## Fabric limits

Fabric accepts only the documented signed, **unlinked existing completed account** route. `autoLoginFloodgate` must permit it (`true` or applicable `no-conflict`); `linked` does not expand Fabric support. `autoRegisterFloodgate: true` does not enable Fabric account creation. Native EasyAuth Floodgate auto-login stays disabled so it cannot bypass FLP's checks.

See [Geyser/Floodgate setup and trust](../integrations/bedrock.md) before enabling these policies.
