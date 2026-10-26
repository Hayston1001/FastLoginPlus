# AuthMeReloaded

AuthMe runs on Bukkit/Paper/Folia backends, not as the backend JAR on Velocity/BungeeCord. Its optional proxy companions are different plugins. Install a version compatible with your server and check its startup separately from FLP.

## 5.x and 6.x are different

FLP includes an AuthMe hook for password/registration actions. AuthMe 6 additionally has premium account state, caches and, on appropriate Paper/Folia versions, pre-join dialogs. FLP detects these capabilities and coordinates its premium handling with them.

During the 6.x takeover FLP enables `settings.enablePremium` and disables AuthMe's own premium verification listener so two systems do not verify the same connection independently. AuthMe's `/premium` and `/freemium` are guarded and point to `/flp premium` and `/flp cracked`. FLP reasserts takeover after applicable AuthMe reload/enable events. This does not make generic server hot reload a supported deployment procedure.

FLP pins the AuthMe proxy companion's `premium.keepOfflineUuidCompatibility` to `false` when managing that companion, leaving FLP `premiumUuid` as the connection UUID authority. It also coordinates AuthMe's own proxy auto-login policy while FLP owns login decisions. Do not maintain an independent contradictory auto-login path in the companion.

## First login and dialogs

A dialog can block before a normal join-time plugin message arrives. The current integration can consume verified standalone identity or trusted forwarded facts early, inspect account ownership, and prepare the allowed account action before AuthMe's gate. Paper configure and Spigot pre-login paths are not identical.

`enablePremium: true` alone does not authenticate a client. A profile UUID, skin or username alone is not sufficient. Legacy forwarding requires validated signed declarations; modern forwarding must be correctly authenticated. Existing password accounts need account-appropriate consent rather than a blind force-register.

The new Bedrock pre-login path requires AuthMe's appropriate name-based registration/force-login APIs, including the capabilities used from 6.0.1. Do not promise that every 5.x hook has this behavior. Generated Bedrock registration credentials on this path are not printed to the player or log. A failed login after creation uses snapshot-checked cleanup; if safe cleanup cannot be verified the log requests operator repair.

## Account safety and recovery

- An unreadable account is not automatically absent. Corrupt premium UUIDs are preserved and rejected for operator diagnosis, not replaced with guessed identity.
- With auto-login disabled, a verified owner can retain the auth plugin's password/registration flow; an unusable passwordless placeholder can need safe release or operator recovery. An ownership mismatch is not permission to delete a row.
- `/flp cracked` performs AuthMe-specific cache and account cleanup. It can remove/reset the auth record, unlike EasyAuth's password-retaining unbind. Back up first and arrange safe re-registration.
- If `respectIpLimit` is enabled, AuthMe's per-IP registration limit is checked on the backend, even behind a proxy.

For blocked dialogs, inspect both proxy and backend logs and follow [troubleshooting](../operations/troubleshooting.md). PacketEvents is not a substitute for FLP's standalone ProtocolLib path; warnings about AuthMe's own optional listener must be interpreted in context.
