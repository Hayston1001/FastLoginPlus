# Premium/cracked lists and admission

## Two logical lists, one authority

Java login uses a premium list and a cracked/non-premium list. They are represented by the same FLP table, not competing databases. `/flp premium` and `/flp cracked` change list state; `offline-whitelist` reads the known list rather than creating a third identity source.

In proxy mode, the proxy's FLP database is authoritative. An AuthMe/EasyAuth account is **local authentication state**, not membership in an FLP list. Creating or deleting an auth account does not by itself synchronize the FLP list, and deleting an FLP record is not an auth-account reset.

## Stored facts are not all consent

| FLP field | Meaning |
| --- | --- |
| `UUID` | Recorded Mojang identity observation used for routing/name matching; it is not proof that the current connection passed verification |
| `Premium` | Consent/confirmed premium account outcome used by the premium login route |
| `SameNameCracked` | Persistent observation of a settled failed premium attempt for an unconsented name, when fallback is enabled |

An administrator's explicit premium operation can also populate the UUID by lookup. That lookup still does not authenticate a later client. Do not edit these columns by hand to bypass ownership checks.

Premium-consented names are verified again on connection. A known cracked name normally uses the password route. An observed premium UUID without consent can still request verification, while the backend preserves an existing password account until account access is allowed.

## Offline whitelist

With `offline-whitelist: true`, unknown offline Java names are denied; known offline names can reach authentication. Premium verification is still required for the premium route. An existing auth-plugin account alone does not make an offline name known to FLP.

To admit an offline name, use the authorized backend command `/flp cracked <player>`. To remove a non-premium FLP record, use `/flp delete <player>`. A premium record is protected from direct deletion: review the account and switch it deliberately first. The list is not a replacement for passwords, Mojang verification or Floodgate verification.

## Same-name offline fallback

`secondAttemptCracked` defaults to `false`. When enabled, a settled definitive failure for an unconsented name can cause later connections to take the password route and persist `SameNameCracked`. The first recorded transition has a one-time notice pointing to `/flp premium`.

- A consented name does not fall back merely because an impostor attempted to connect.
- On standalone servers, explicit session rejection is distinguished from an unreachable session service and an interrupted attempt.
- Proxies do not expose the same verdict. FLP settles an unverified attempt later using a service-reachability check. A client-side network drop while Mojang is reachable can be counted as failure; this is not perfect proof that the client was cracked.
- A passing verification or an explicit premium/cracked operation clears the observation. With the option disabled, the observation is ignored and reconciled away at load time.

Keep this off unless you understand the trade-off. Do not enable it as a general workaround for Mojang outages or a wrongly configured proxy.
