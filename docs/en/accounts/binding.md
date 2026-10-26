# Binding and switching

Use the [commands reference](commands.md) for permissions and console restrictions. An account action must be performed by its authenticated holder or an authorized administrator. A premium-looking name is not permission to overwrite another account.

Keep the auth plugin's pre-authentication command restrictions enabled. Do not whitelist account-management commands for unauthenticated players just to make `/flp` available before login. Classic command permission checks are not themselves password authentication.

## Classic Bukkit/Folia accounts

1. For self-service, complete the auth plugin's login first. Run `/flp premium` and repeat only when the premium-warning prompt asks for confirmation.
2. In proxy mode the backend relays the request; the proxy changes the FLP list. Wait for the **final** result, not just the hand-off message.
3. If `kick-toggle` is enabled the proxy/server disconnects the affected player; otherwise the current session stays. Reconnect manually to use the new route and prove the premium identity.
4. Test the next login and record the connection UUID. A command does not authenticate the next connection or migrate player data.

`/flp cracked` clears FLP premium state and changes the next route. **AuthMe cleanup is destructive:** current classic integration removes/reset its account record and clears premium caches, so re-registration may be needed. Do not assume it preserves a player's old password. For other auth plugins, equivalent cleanup is not generally implemented; an automatically generated password may need operator recovery.

Classic standalone other-player commands and proxy-relayed commands do not have identical kick behavior. Follow actual feedback and schedule a manual reconnect rather than promising every admin command immediately kicks its target.

## Fabric/EasyAuth password account to premium

1. Log in to the existing EasyAuth password account. Its stored state must be a consistent password account.
2. Run `/flp premium`. The request uses the normal proxy toggle channel. With `premium-warning: true`, follow the proxy's confirmation and run the command again as prompted.
3. The proxy records consent and resolves the target Mojang UUID. If `kick-toggle` is enabled it kicks; otherwise leave and reconnect manually.
4. A later connection must pass Mojang verification and match account/consent checks before the backend converts it and completes EasyAuth login.

The existing password is retained as a dormant credential, not replaced with a random one. The pending message is not the conversion itself. There is no transfer, cookie or temporary ten-minute backend binding window in this flow. Missing proxy trust, failed persistence or lost connectivity requires checking both sides before retrying; do not erase the password account.

An administrator may authorize `/flp premium <player>` without proving the target's password. That is an explicit privileged operation, not ordinary self-service. It still requires a later verified connection and does not erase the password.

## Fabric/EasyAuth back to password or registration

- For a bound account with a retained password, `/flp cracked` clears the local premium binding and restores the password route, preserving that credential.
- For a bound account **without** a password, the command warns that the name will be opened for registration. Repeat the same operation within **30 seconds** to confirm. This releases it to registration rather than revealing a nonexistent password. Arrange a safe re-registration window; the next registrant can claim it.
- The self cracked command is operator-restricted and requires the account to be authenticated. Administrators can act on another name.
- `/flp delete <player>` removes only a non-premium FLP record at the proxy. It does not delete the EasyAuth account; bound local accounts must be handled first.

These operations involve both the proxy record and local EasyAuth storage and are not a distributed transaction. A local save failure after a request was sent can leave states requiring operator reconciliation. Review logs and persistent records, then test reconnect and restart.

## Recovery precautions

Back up both stores first, use exact player names, and distinguish FLP consent from auth-plugin ownership. Do not "repair" an ownership conflict by manually attaching a victim's UUID. Restoring a backup or changing UUID policy can also affect inventories and permissions; see [UUIDs and player data](uuid.md).
