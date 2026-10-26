# UUIDs, name changes and player data

## Verification and the delivered UUID are independent

`premiumUuid` defaults to `true`. It selects which UUID the backend sees **after** premium verification; it does not enable or disable that verification.

| Value | Verified Java connection UUID |
| --- | --- |
| `true` | Mojang UUID |
| `false` | Name-derived offline UUID |

An unverified connection does not gain a premium identity by enabling this setting. In standalone mode FLP injects the premium UUID when enabled. On a proxy the verified profile already has the premium UUID, so disabling it rewrites the profile to the offline UUID. Configure this on the proxy in proxy mode.

The verified Mojang UUID and connection UUID can therefore differ. AuthMe/EasyAuth integration uses verified identity facts for ownership, not the appearance of a UUID or a second name lookup. A version-4 UUID is not proof that forwarding was authenticated.

## Before changing UUID policy

1. Back up worlds/player data, permissions, economy, inventories and auth/FLP databases.
2. Record both UUIDs for representative accounts and inspect how each plugin keys data.
3. Test in an isolated copy. A new inventory is often data under another UUID, not deleted data.
4. Plan migration separately for every affected plugin, or retain the old policy. FLP does not perform this migration.

Do not change `premiumUuid` casually on a live server or copy another server's config without review. In proxy mode all participating backends need a consistent understanding of forwarded identities.

## Name changes

`nameChangeCheck` queries a new name and can find/update an existing FLP record by Mojang UUID. It synchronizes FLP's observation; it does not rename every plugin's account or move world data.

The Fabric/EasyAuth path can follow an existing premium account by a verified UUID under its supported SQLite/account-consistency conditions. It must not overwrite an account already owned by another name/identity. This is not a general name-migration tool for all EasyAuth storage backends.

Bedrock/Floodgate identities follow their own UUID and linked-account rules. Do not apply the Java premium UUID table to a Bedrock connection or derive authentication from a username prefix.
