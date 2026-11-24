# Other authentication plugins

## Available integrations

| Platform | Authentication plugins |
| --- | --- |
| Bukkit/Folia | AuthMe, CrazyLogin, LogIt, LoginSecurity, UltraAuth, xAuth, Passky, UserLogin, MyLogin |
| BungeeCord | BungeeAuth |
| Velocity | Install an authentication plugin on participating backends |
| Fabric | EasyAuth; see the [Fabric installation guide](../installation/fabric.md) |

This list does not guarantee every plugin version is compatible with your server. Check each plugin's requirements and startup log, then check login with test accounts before opening the server.

FLP selects exactly one enabled backend provider at startup, in this order: AuthMe, CrazyLogin, LogIt, LoginSecurity, UltraAuth, xAuth, Passky, UserLogin, MyLogin. Other enabled providers are listed in the startup log and ignored. If the selected provider fails to initialize, logins stay closed instead of falling back to another account store, and the choice is not repeated until the server restarts.

## Adapter requirements

- **UserLogin 2.13.1** requires its default YAML storage, offline UUIDs and its own automatic login disabled.
- **MyLogin 1.3.0** requires `ip-remember: false`; otherwise FLP cannot tell its own action from a native one.
- **CrazyLogin** no longer needs ProtocolLib; ProtocolSupport alone is enough for the FLP hook.
- **LogIt and xAuth** are legacy hosts. If they cannot start on your server, FLP reports the failure; it does not patch the host.

## Capabilities are not interchangeable

Automatic registration, login and account switching vary by authentication plugin; do not assume they behave like AuthMe. If a plugin generated a player's password, switching back to password login may require that plugin's own recovery procedure.

Folia requires the auth plugin itself to support Folia. Moving from Bukkit FLP to Folia FLP does not adapt another plugin's scheduler calls.

The BungeeAuth hook is separate from backend AuthMe and does not create a native proxy `/flp` management command. Do not combine multiple independent auto-login systems without a clear ownership and identity model.

If a plugin is incompatible or fails to start, keep the logs and [report the problem](../operations/reporting.md) rather than removing safety checks.
