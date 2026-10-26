# Troubleshooting and FAQ

Start with one reproducible attempt. Record deployment, exact versions, account state, policy and what the player saw. Do not repeatedly change unrelated switches; preserve both sides' logs first.

## Premium player still gets a password prompt

1. Check whether this connection actually passed Mojang verification. A Mojang name lookup, skin or stored UUID is not enough.
2. Check the authoritative `autoLogin` policy and local auth account state. An existing password account without consent can correctly remain password-locked.
3. If no account exists, inspect `auto-register-unknown`. Off intentionally keeps registration.
4. If this is a proxy backend, check forwarded facts/trust and proxy configuration, not just the backend's same-named options.
5. Only after the holder logs in should they use the [binding procedure](../accounts/binding.md).

## Stuck in an AuthMe pre-join dialog

Confirm AuthMe version, FLP takeover and the appropriate early path. On Paper the dialog can appear before join-time plugin messages; Spigot uses a different pre-login path. Check forwarding mode/secret, proxy allowlist and validated declarations, then account ownership and registration API failures. Do not add a second verification listener, change UUID policy at random or treat a blocking password dialog as permission to erase an account.

## Proxy requests have no effect

- Run `/flp` on the backend, not the proxy console.
- Check the generated proxy ID, backend trust list and required restart. Empty trust prevents message-based operations.
- Check `verify-backend-messages` and old-peer policy compatibility. Do not permanently disable checking to hide a mismatch.
- Classic empty backends queue requests locally; another backend having a player does not drain that queue. Fabric empty-server console requests fail instead of persisting.
- Distinguish immediate hand-off from final result. Collect proxy logs, backend feedback and actual database state.

## Mojang unavailable / invalid session

An unfinished lookup is refused with an authentication-server message, not stored as a new cracked account. `invalid-session` can mean a genuine session rejection; ask the player to refresh their launcher session and use the correct address. Check DNS/TLS/outbound connectivity and rate limits. Proxy-native session verification is outside FLP's retry-count setting. Repeated reconnects can worsen limits.

## Account ownership conflict / corrupt UUID

Keep the existing row. Check exact name, verified UUID, consent and local auth state under operator control. A mismatch, malformed stored UUID or failed read is not a new empty account. Restore/reconcile from a trusted backup; do not paste a UUID supplied by a claimant or delete the auth store as a shortcut.

## Premium auto-login stopped after disabling it

`autoLogin: false` deliberately stops passwordless actions, including already converted accounts. Some have no usable password. Recover according to the auth plugin and [switching rules](../accounts/binding.md), not by assuming a password was generated for every premium account.

## Inventory or permissions changed

Compare old/new connection UUIDs and `premiumUuid` on the authoritative side. FLP does not migrate UUID-indexed data. Restore or separately migrate the affected plugin's data in a test copy; do not overwrite world files while players are online.

## Fabric rejects login or admin operations

Check Minecraft/EasyAuth versions, modern forwarding, `config/fastloginplus-fabric.yml`, trusted IDs and all required EasyAuth policies. Read the storage warning: a non-SQLite backend is not verified safe. Bedrock new/linked accounts are outside the supported path. Configuration changes need a backend restart; no `/flp reload` exists. A console command needs an online carrier.

## Too many players share a rate limit / missing skins / placeholders

Validate forwarded real IPs before increasing anti-bot limits. For textures, check the proxy's `forwardSkin`, custom SkinsRestorer state and profile caches. For placeholders, check the Bukkit/Folia expansion and remember it is cached display state, not proof of authentication.

If a ProtocolLib encryption-response mapping warning appears on a standalone server, verify a compatible packet-library/server combination with debug logging and a real client. Do not assume newer version numbers guarantee interception, or that falling into a password flow proves the warning harmless.

If these checks do not explain it, [report the problem](reporting.md) with a minimal, sanitized reproduction.
