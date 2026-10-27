# Binding and switching

## Bukkit/Paper/Folia: bind a premium account

1. Log in to your password account using the authentication plugin.
2. Enter `/flp premium`. If prompted for confirmation, enter the command again.
3. Wait for the final result, then reconnect manually using the corresponding premium account.
4. Confirm the next login and player data are as expected.

### Notes

- In proxy mode, a sent request does not mean the list change has completed. Wait for final feedback.
- `kick-toggle` controls whether applicable switches disconnect the player; otherwise leave and reconnect manually.
- The command does not verify the next connection or migrate player data. When an administrator acts on another player, arrange a reconnect according to the feedback.

## Bukkit/Paper/Folia: return to password login

**Back up the authentication database first.** AuthMe can remove or reset an account, and the player may need to register again; do not assume the original password is retained.

1. Confirm the player is logged in and has permission to run `/flp cracked`.
2. Enter `/flp cracked` and wait for the final result.
3. Reconnect as instructed and follow the authentication plugin's password or registration flow.

### Notes

Cleanup and password recovery differ between authentication plugins. If the plugin generated a password, check its recovery procedure before switching. Administrators can use `/flp cracked <player>`; see [commands and permissions](commands.md).

## Fabric/EasyAuth: bind a premium account

1. Log in to the existing EasyAuth password account.
2. Enter `/flp premium`. If prompted for confirmation, enter the command again.
3. Wait for the final result, then reconnect manually using the corresponding premium account.
4. After verification, confirm that EasyAuth completes binding and login.

### Notes

The original password is retained and can be restored by unbinding. A sent request does not mean binding is complete. If it fails, check proxy and backend logs before retrying; do not delete the password account. An administrator can authorize `/flp premium <player>`, but the player must still reconnect with their verified premium identity.

## Fabric/EasyAuth: return to password login or registration

**An account without a password is opened for registration after confirmation.** Arrange a safe registration time first so another person cannot claim it.

1. Log in to the account and confirm you have administrator permission.
2. Enter `/flp cracked`.
3. If warned that the account has no password and will be opened for registration, repeat the same command within 30 seconds to confirm.
4. Reconnect as instructed: use the original password if one exists, or register again if it does not.

### Notes

Administrators can specify another player. `/flp delete <player>` only requests deletion of a non-premium FLP record on the proxy; it does not delete the EasyAuth account. Handle a bound local account first. If a proxy request was sent but the local save failed, check logs and account state on both sides before retrying.

## Requirements for all operations

Account actions must be performed by an authenticated owner or authorized administrator. Keep the authentication plugin's restrictions on commands before login; do not allow unauthenticated players to use `/flp`. Back up FLP and authentication-plugin data before switching or recovering accounts. UUID changes can affect inventories and permissions; see [UUIDs and player data](uuid.md).
