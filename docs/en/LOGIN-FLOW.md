# Login flow and identity trust

This page outlines the responsibilities of the main parts of FLP's login flow. For administrator procedures, see [login methods](accounts/login.md) and [binding and switching](accounts/binding.md).

## From connection to login

1. FLP selects a login method using its list and server policies. Premium Java login requires verification of the current connection; finding a username or UUID does not prove account ownership.
2. On a standalone server, FLP works with the packet library to verify the connection. In a proxy deployment, the proxy verifies Mojang or Floodgate identity, manages network policy and FLP lists, and supplies identity and policy to the backend.
3. The backend checks the local authentication account and uses the proxy policy and account state to decide whether to register, log in, require a password or reject. A standalone server performs these responsibilities locally.
4. Login is complete only when the authentication plugin confirms the account action. Updating the list and updating the local account are separate operations; success in one does not imply success in both.

Binding an existing password account requires its owner's consent. A bound account cannot be opened by another connection solely because its name matches. Capabilities vary by authentication plugin and platform; not all integrations perform the same early login checks.

## Trust boundaries

- The proxy is the source of the network's FLP list and policy; backend authentication plugins manage their own accounts and passwords. A backend must not override a valid proxy policy with a local copy of the setting.
- The backend must validate information from the proxy and its association with the current connection. A username, UUID shape, skin or plugin message channel name is not proof of identity.
- Velocity modern forwarding requires a correctly configured secret. Legacy forwarding does not authenticate the whole forwarding chain. Both require restricted backend ports; an FLP proxy ID allowlist is not a firewall.
- An identity or account read failure is not permission to create or open an account. Protected accounts must remain locked or be rejected.

## Fabric and Bedrock

The Fabric backend combines Velocity's forwarded identity and policy with EasyAuth account state. To bind a password account, its owner must first log in and consent, then reconnect manually for verification; the original password is retained.

Bedrock support depends on the authentication plugin. Fabric/EasyAuth currently only allows qualifying unlinked Floodgate players to log in to existing accounts; it does not automatically create Bedrock accounts. See [Bedrock integration](integrations/bedrock.md).

For code entry points and module relationships, see the [architecture overview](development/architecture.md).
