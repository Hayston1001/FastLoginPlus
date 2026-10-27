# Premium/cracked lists and admission

## Enable the offline whitelist

**Back up the authentication database first.** `/flp cracked` can change existing accounts; with AuthMe it can remove or reset an account record.

1. Stop the server. In a proxy deployment, stop the proxy and edit its FLP configuration; on a standalone server, edit the local FLP configuration.
2. Set `offline-whitelist` to `true`, then start the server.
3. On a backend, run `/flp cracked <player>` as an authorized administrator to add each offline player who needs access.
4. Use test accounts to check that known players can reach authentication and unknown offline players are refused.

### Notes

An existing authentication-plugin account does not mean FLP already knows the name. The whitelist does not replace password, Mojang or Floodgate authentication. See [commands and permissions](commands.md).

## Delete a non-premium list record

1. Confirm the target name and account state, and back up FLP and authentication-plugin data.
2. On a backend, run `/flp delete <player>` and wait for the final result.

### Notes

`/flp delete` does not directly delete premium records or reset authentication-plugin accounts. Review and deliberately switch a premium account first. Switching can change or remove an authentication account; read [binding and switching](binding.md) before acting.

## What the lists do

FLP distinguishes premium and non-premium lists. `/flp premium` and `/flp cracked` change list status; `offline-whitelist` uses those known records to restrict admission.

In proxy mode, the proxy's FLP database is authoritative. An AuthMe/EasyAuth account is **local authentication state**, not membership in an FLP list. Creating or deleting an auth account does not by itself synchronize the FLP list, and deleting an FLP record is not an auth-account reset.

Premium players on the list still need verification on each connection. Non-premium players normally use password login. Do not edit the database directly to bypass account ownership checks.

## Same-name offline fallback

`secondAttemptCracked` defaults to `false`. When enabled, a confirmed premium verification failure for a name without binding consent can cause later connections to use password login. The first change is accompanied by a notice pointing to `/flp premium`.

- A consented name does not fall back merely because an impostor attempted to connect.
- On standalone servers, explicit session rejection is distinguished from an unreachable session service and an interrupted attempt.
- Proxy mode cannot always distinguish failed verification from a player's network interruption. A disconnected player can be counted as a failure even when Mojang is reachable.
- Successful verification or a premium/cracked command clears the previous fallback record.

Keep this off unless you understand the trade-off. Do not enable it as a general workaround for Mojang outages or a wrongly configured proxy.
