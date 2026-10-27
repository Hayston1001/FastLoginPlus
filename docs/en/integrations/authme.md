# AuthMeReloaded

Install AuthMe on Bukkit, Paper or Folia. In a proxy network, each backend requiring authentication needs compatible versions of AuthMe and FLP; do not put the backend AuthMe plugin in the proxy's `plugins/` directory.

## Choose a version

| Feature | AuthMe 5.x | AuthMe 6.x |
| --- | --- | --- |
| Password accounts | Registration and password login; FLP can provide automatic login | Also supported |
| Premium account management | Use FLP's account commands | FLP coordinates AuthMe's premium account features; use FLP's commands |
| Pre-join dialogs | Does not have AuthMe 6's dialog feature | Available on applicable Paper/Folia versions |
| Bedrock automatic registration and login | The newer early-login flow is not guaranteed | Requires the appropriate AuthMe APIs; 6.0.1 provides the required capabilities |

Choose a build compatible with your server and confirm that AuthMe and FLP both load in the startup log. Verify your particular versions and server combination.

## Commands and settings

- Use `/flp premium` to bind a premium account and `/flp cracked` to return to password login. Read [binding and switching](../accounts/binding.md) first.
- With AuthMe 6, FLP manages its premium features. Do not configure a separate conflicting premium verification or proxy auto-login system.
- When FLP manages an AuthMe proxy companion, it sets `premium.keepOfflineUuidCompatibility: false`. FLP's `premiumUuid` setting determines the UUID used for the player.
- To honor AuthMe's per-IP registration limit, enable `respectIpLimit` in the backend FLP configuration.

## Back up before switching

`/flp cracked` can remove or reset an AuthMe account record. Back up the authentication database first and make sure the player can register again safely. This differs from EasyAuth, which retains the original password.

An existing password account is not taken over just because its name belongs to a premium player. The player must log in and agree to binding. If a login dialog blocks entry, check proxy and backend logs and see [troubleshooting](../operations/troubleshooting.md).
