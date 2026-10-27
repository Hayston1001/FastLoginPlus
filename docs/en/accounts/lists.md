# Premium/cracked lists and admission

## What the lists do

FLP distinguishes premium and non-premium lists. `/flp premium` and `/flp cracked` change list status; `offline-whitelist` uses those known records to restrict admission.

In proxy mode, the proxy's FLP database is authoritative. An AuthMe/EasyAuth account is **local authentication state**, not membership in an FLP list. Creating or deleting an auth account does not by itself synchronize the FLP list, and deleting an FLP record is not an auth-account reset.

Premium players on the list still need verification on each connection. Non-premium players normally use password login. Do not edit the database directly to bypass account ownership checks.

## Offline whitelist

With `offline-whitelist: true`, unknown offline Java names are denied; known offline names can reach authentication. Premium verification is still required for the premium route. An existing auth-plugin account alone does not make an offline name known to FLP.

To admit an offline name, use the authorized backend command `/flp cracked <player>`. To remove a non-premium FLP record, use `/flp delete <player>`. A premium record is protected from direct deletion: review the account and switch it deliberately first. The list is not a replacement for passwords, Mojang verification or Floodgate verification.

## Same-name offline fallback

`secondAttemptCracked` defaults to `false`. When enabled, a confirmed premium verification failure for a name without binding consent can cause later connections to use password login. The first change is accompanied by a notice pointing to `/flp premium`.

- A consented name does not fall back merely because an impostor attempted to connect.
- On standalone servers, explicit session rejection is distinguished from an unreachable session service and an interrupted attempt.
- Proxy mode cannot always distinguish failed verification from a player's network interruption. A disconnected player can be counted as a failure even when Mojang is reachable.
- Successful verification or a premium/cracked command clears the previous fallback record.

Keep this off unless you understand the trade-off. Do not enable it as a general workaround for Mojang outages or a wrongly configured proxy.
