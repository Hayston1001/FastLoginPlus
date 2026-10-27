# UUIDs, name changes and player data

## Change the UUID used by players

{% hint style="warning" %}
**Back up player and account data first.** Changing UUIDs can make players load different inventories and permissions; FLP does not migrate that data automatically.
{% endhint %}

1. Back up worlds, player data, other plugin data, and the FLP and authentication-plugin databases.
2. On a test copy, record existing UUIDs and plan migration for affected plugins; keep the current setting if migration is not needed.
3. Stop the server. Edit the local FLP `config.yml` on a standalone server, or the proxy's FLP `config.yml` in a proxy deployment.
4. Set `premiumUuid` to the desired value, start the server and check login, inventories and permissions with test accounts.
5. Only after confirming the result, apply the same plan to production.

### Option details

`premiumUuid` defaults to `true`. It selects which UUID the backend sees **after** premium verification; it does not enable or disable that verification.

| Value | Verified Java connection UUID |
| --- | --- |
| `true` | Mojang UUID |
| `false` | Name-derived offline UUID |

This setting does not give an unverified player a premium identity. In proxy mode, configure it on the proxy.

A different inventory can mean data was loaded under another UUID, not necessarily that it was deleted. Do not copy another server's setting without checking it.

## Name changes

`nameChangeCheck` queries a new name and can find/update an existing FLP record by Mojang UUID. It synchronizes FLP's observation; it does not rename every plugin's account or move world data.

The Fabric/EasyAuth path can follow an existing premium account by a verified UUID under its supported SQLite/account-consistency conditions. It must not overwrite an account already owned by another name/identity. This is not a general name-migration tool for all EasyAuth storage backends.

Bedrock/Floodgate identities follow their own UUID and linked-account rules. Do not apply the Java premium UUID table to a Bedrock connection or derive authentication from a username prefix.
