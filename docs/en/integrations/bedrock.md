# Geyser and Floodgate

Geyser lets Bedrock players connect to Java servers. Floodgate lets Bedrock players join supported servers without a Java account. FLP does not replace them: install and configure them using the [Geyser](https://geysermc.org/wiki/geyser/setup) and [Floodgate](https://geysermc.org/wiki/floodgate/setup) guides first.

## How players log in

| Setup | Player login |
| --- | --- |
| Geyser + Floodgate | FLP's Bedrock policy can allow automatic registration or login. Support depends on the backend authentication plugin |
| Geyser without Floodgate | Login depends on Geyser's authentication settings. When a player logs in with a premium Java account, FLP can treat them as a Java player |

A matching username alone does not prove ownership of a premium Java account. Do not grant premium passwordless login to an unverified player.

In a proxy network, configure Bedrock policy on the proxy; see [Velocity](../configuration/velocity.md) or [BungeeCord](../configuration/bungeecord.md). The backend still needs a compatible authentication plugin. For AuthMe, see the [version comparison](authme.md). Back up account data before changing Floodgate's player-name prefix: a name change can affect existing accounts.

## Before enabling Bedrock login

- **AuthMe name characters.** AuthMe only accepts names matching `settings.restrictions.allowedNicknameCharacters` in `plugins/AuthMe/config.yml` (default `[a-zA-Z0-9_]*`). Floodgate's default prefix is a dot, so a name such as `.Steve` is rejected until the pattern includes it, for example `[a-zA-Z0-9_.]*`. Widen only the characters you need, or configure Floodgate without a prefix.
- **Version alignment.** Geyser chooses the Java protocol version it announces. The proxy and backend must support that Minecraft version; for example, a Geyser release that announces 26.3 needs a matching Velocity and Paper line. FLP follows the versions it runs on and cannot add protocol support.
- **Name conflicts.** The conflict check compares the forwarded name. With a non-empty prefix that cannot be a Java name, a conflict cannot occur; to test `allowFloodgateNameConflict: false`, use an empty prefix (or one that produces a valid Java name) and accept the account-name changes that follow.

## Fabric support

Velocity with Fabric/EasyAuth currently supports automatic login only for qualifying Floodgate players **without a linked Java account** and with an existing EasyAuth account. The account must be fully registered and its name and UUID must match the player; the proxy must also allow automatic login.

This setup does not currently create Bedrock accounts automatically, support linked Floodgate account login, or convert Bedrock accounts to premium Java accounts. If login fails, do not disable identity checks or invent a UUID; check the account and proxy settings first. See the [Fabric guide](../installation/fabric.md) for installation requirements.
