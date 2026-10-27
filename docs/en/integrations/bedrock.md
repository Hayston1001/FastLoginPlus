# Geyser and Floodgate

Geyser lets Bedrock players connect to Java servers. Floodgate lets Bedrock players join supported servers without a Java account. FLP does not replace them: install and configure them using the [Geyser](https://geysermc.org/wiki/geyser/setup) and [Floodgate](https://geysermc.org/wiki/floodgate/setup) guides first.

## How players log in

| Setup | Player login |
| --- | --- |
| Geyser + Floodgate | FLP's Bedrock policy can allow automatic registration or login. Support depends on the backend authentication plugin |
| Geyser without Floodgate | Login depends on Geyser's authentication settings. When a player logs in with a premium Java account, FLP can treat them as a Java player |

A matching username alone does not prove ownership of a premium Java account. Do not grant premium passwordless login to an unverified player.

In a proxy network, configure [Bedrock policy](../configuration/bedrock.md) on the proxy. The backend still needs a compatible authentication plugin. For AuthMe, see the [version comparison](authme.md). Back up account data before changing Floodgate's player-name prefix: a name change can affect existing accounts.

## Fabric support

Velocity with Fabric/EasyAuth currently supports automatic login only for qualifying Floodgate players **without a linked Java account** and with an existing EasyAuth account. The account must be fully registered and its name and UUID must match the player; the proxy must also allow automatic login.

This setup does not currently create Bedrock accounts automatically, support linked Floodgate account login, or convert Bedrock accounts to premium Java accounts. If login fails, do not disable identity checks or invent a UUID; check the account and proxy settings first. See the [Fabric guide](../installation/fabric.md) for installation requirements.
