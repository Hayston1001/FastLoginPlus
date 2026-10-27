# Troubleshooting and FAQ

Start with one reproducible attempt. Record deployment, exact versions, account state, policy and what the player saw. Do not repeatedly change unrelated switches; preserve both sides' logs first.

## Premium player still gets a password prompt

1. Check whether this connection actually passed Mojang verification. A Mojang name lookup, skin or stored UUID is not enough.
2. Check FLP's `autoLogin` and the authentication plugin's account state. In a proxy deployment, check the proxy configuration. An unbound password account correctly continues to require its password.
3. If no account exists, inspect `auto-register-unknown`. Off intentionally keeps registration.
4. With a proxy, follow the installation guide to check forwarding, the secret and the backend's allowed proxy IDs.
5. Only after the holder logs in should they use the [binding procedure](../accounts/binding.md).

## Stuck in an AuthMe login dialog

1. Confirm AuthMe and FLP are compatible with your server and check both startup logs.
2. If using a proxy, follow the installation guide to check forwarding mode, the secret and the backend's allowed proxy IDs.
3. Check whether the account already has a password and look for account or registration errors in both logs.
4. If the issue persists, preserve account data and [report the problem](reporting.md). Do not delete the account or change UUID settings at random to bypass the dialog.

## Proxy requests have no effect

- Run `/flp` on the backend, not the proxy console.
- Check the generated proxy ID, backend trust list and required restart. Empty trust prevents message-based operations.
- Check `verify-backend-messages` and old-peer policy compatibility. Do not permanently disable checking to hide a mismatch.
- An empty Bukkit/Folia backend holds pending requests until a player joins that backend. Fabric console requests that need forwarding fail when the server is empty.
- Distinguish immediate hand-off from final result. Collect proxy logs, backend feedback and actual database state.

## Mojang unavailable / invalid session

An unfinished lookup is refused with an authentication-server message, not stored as a new cracked account. `invalid-session` can mean a genuine session rejection; ask the player to refresh their launcher session and use the correct address. Check DNS/TLS/outbound connectivity and rate limits. Proxy-native session verification is outside FLP's retry-count setting. Repeated reconnects can worsen limits.

## Account ownership conflict / corrupt UUID

Keep the existing row. Check exact name, verified UUID, consent and local auth state under operator control. A mismatch, malformed stored UUID or failed read is not a new empty account. Restore/reconcile from a trusted backup; do not paste a UUID supplied by a claimant or delete the auth store as a shortcut.

## Premium auto-login stopped after disabling it

`autoLogin: false` deliberately stops passwordless actions, including already converted accounts. Some have no usable password. Recover according to the auth plugin and [switching rules](../accounts/binding.md), not by assuming a password was generated for every premium account.

## Inventory or permissions changed

Compare old/new connection UUIDs and the `premiumUuid` setting. In a proxy deployment, check the proxy configuration. FLP does not migrate data stored by UUID. Restore or separately migrate the affected plugin's data in a test copy; do not overwrite world files while players are online.

## Fabric rejects login or admin operations

Check Minecraft/EasyAuth versions, modern forwarding, `config/fastloginplus-fabric.yml`, trusted IDs and all required EasyAuth policies. Read the storage warning: a non-SQLite backend is not verified safe. Bedrock new/linked accounts are outside the supported path. Configuration changes need a backend restart; no `/flp reload` exists. A console command needs an online carrier.

## Too many players share a rate limit / missing skins / placeholders

Validate forwarded real IPs before increasing anti-bot limits. For textures, check the proxy's `forwardSkin`, custom SkinsRestorer state and profile caches. For placeholders, check the Bukkit/Folia expansion and remember it is cached display state, not proof of authentication.

If a ProtocolLib encryption-response mapping warning appears on a standalone server, verify a compatible packet-library/server combination with debug logging and a real client. Do not assume newer version numbers guarantee interception, or that falling into a password flow proves the warning harmless.

If these checks do not explain it, [report the problem](reporting.md) with a minimal, sanitized reproduction.
