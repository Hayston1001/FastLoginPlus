# EasyAuth

Use the [Fabric installation guide](../installation/fabric.md). This integration targets Minecraft 26.2 and EasyAuth 3.4.4, accepting the 3.4.x patch range with version diagnostics. It does not provide Bukkit/Folia EasyAuth support.

## Responsibility split

Velocity verifies Java/Floodgate identity, applies network policy and owns FLP consent/list records. FabricProxy-Lite authenticates modern forwarding. FLP Fabric checks the identity declaration against the actual profile, reads EasyAuth's account state, and decides/executes local account access.

Disable EasyAuth's independent premium/Floodgate auto-login, session reuse and authentication bypasses as listed in the installation guide. FLP rechecks loaded EasyAuth policy for operations; it neither rereads EasyAuth configuration files nor supplies hot reload.

## Account states

- **Open:** no account or a supported unclaimed entry. A verified Java identity may be registered according to declared policy.
- **Password:** a consistent offline account with a nonempty credential. It stays locked until its holder authenticates; premium conversion requires recorded consent.
- **Bound:** a premium-owned entry with its verified UUID. Another UUID or an unverified connection cannot use the binding.
- **Conflict/unavailable:** inconsistent identity fields or a failed account check. Refuse or keep authentication locked, never guess a new owner.

Password-to-premium conversion retains the password. Unbinding can restore it. An originally passwordless account must be deliberately released for registration; see [binding](../accounts/binding.md). `premiumUuid: false` changes the connection UUID, not the Mojang ownership UUID stored by the integration.

## Storage and failure boundary

The supported path uses local SQLite, with direct account reads that distinguish SQL errors from missing records. Other EasyAuth stores are unverified and emit a warning because their APIs can return null for errors. A loader-compatible JAR or a successful first login does not prove those stores are safe.

Local account updates check save success and restore in-memory fields on failure. If binding was saved but the current login cannot finish, reconnect may be required. A proxy request and local account write are not an atomic cross-server transaction. After failures inspect both persistent stores, not just chat feedback.

Name following is limited to verified identities and consistent, non-conflicting accounts. No inventory migration, arbitrary storage conversion, linked Bedrock binding or multi-backend migration is promised. Bedrock access has the narrower [existing-account boundary](bedrock.md).
