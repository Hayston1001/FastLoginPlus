# EasyAuth

Use the [Fabric installation guide](../installation/fabric.md). This integration targets Minecraft 26.2 and EasyAuth 3.4.4, accepting the 3.4.x patch range with version diagnostics. It does not provide Bukkit/Folia EasyAuth support.

## Configuration and login

Set FLP login policies on Velocity, and install EasyAuth and manage local accounts on the Fabric backend. Follow the [installation guide](../installation/fabric.md) to prevent EasyAuth's own automatic login and authentication bypasses from conflicting with FLP. Restart the server after changing configuration.

## Account states

- Premium player without an account: automatic registration and login may be available if server policy allows it.
- Existing password account: its owner must log in and agree to binding before using premium passwordless login.
- Bound premium account: only the verified account owner can use the binding to log in.
- Conflicting or unreadable account information: login may be refused; check the logs and database.

Password-to-premium conversion retains the password. Unbinding can restore it. An originally passwordless account must be deliberately released for registration; see [binding](../accounts/binding.md). `premiumUuid: false` changes the connection UUID, not the Mojang ownership UUID stored by the integration.

## Storage and troubleshooting

EasyAuth's local SQLite storage is currently supported. Other EasyAuth storage types are unverified and produce a warning.

If a player cannot log in after binding, try reconnecting first. If that does not help, check logs and data on both proxy and backend. Do not delete an account based only on a chat message.

Name following is limited to verified identities and consistent, non-conflicting accounts. No inventory migration, arbitrary storage conversion, linked Bedrock binding or multi-backend migration is promised. Bedrock access has the narrower [existing-account boundary](bedrock.md).
