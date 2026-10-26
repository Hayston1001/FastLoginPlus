# Other authentication plugins

## Hooks present in the classic source

| Platform | Hook candidates |
| --- | --- |
| Bukkit/Folia | AuthMe, CrazyLogin, LogIt, LoginSecurity, UltraAuth, xAuth, Passky |
| BungeeCord | BungeeAuth |
| Velocity | No native auth-account hook; coordinates participating backends |
| Fabric | Dedicated EasyAuth integration, not the generic classic hook API |

This is an implementation inventory, **not** a guarantee that every historical or future release works. Some integrations use old APIs/local compile JARs. Verify the actual plugin version, server, threading model and startup hook selection. Prefer one authentication owner per backend; if several candidates are installed, hook selection order is not a multi-auth composition system.

## Capabilities are not interchangeable

Generic hooks provide registration/login/account checks to varying degrees. They do not automatically provide AuthMe 6's early dialogs, safe placeholder release, premium UUID bookkeeping or cracked cleanup. A plugin that auto-registered a random password may need its own supported recovery workflow when the player switches to cracked.

Folia requires the auth plugin itself to support Folia. Moving from Bukkit FLP to Folia FLP does not adapt another plugin's scheduler calls.

The BungeeAuth hook is separate from backend AuthMe and does not create a native proxy `/flp` management command. Do not combine multiple independent auto-login systems without a clear ownership and identity model.

## Before calling an integration supported

Check fresh registration, existing password login, returning premium login, disabled auto-login, account switching, disconnect/reconnect, failed reads/writes and restart persistence. Keep genuine client evidence separate from mocked unit tests or compile success. Report unsupported APIs/version errors rather than removing safety checks.
