# Features and boundaries

FLP is an actively maintained fork of [FastLogin](https://github.com/TuxCoding/FastLogin). It integrates identity verification with an authentication plugin; it is not a replacement for that plugin or a way to give an unverified client a premium identity.

## What it does

- Selects Java connections that need Mojang verification, then uses the server or proxy's login handshake to establish ownership.
- Coordinates automatic registration and passwordless login where policy and account ownership allow it. Existing password accounts do not become ownerless just because their names match paid accounts.
- Maintains premium/cracked records and optional admission for known offline names.
- Selects premium or offline connection UUIDs, forwards skins, and respects supported SkinsRestorer custom skins.
- Supports independent Bukkit/Folia servers and BungeeCord/Velocity networks. Fabric 26.2 uses a separate Velocity/EasyAuth backend integration.
- Integrates Floodgate identities within each platform's supported limits.
- Provides connection limits, English/Chinese messages, update checks and backend management commands.

## What it does not do

- It does not sell or supply Minecraft accounts, authenticate a name by spelling alone, or turn an offline client into an authenticated Java client.
- It does not automatically migrate inventories, worlds, permissions or other plugins' records between UUIDs.
- It does not replace Geyser, Floodgate, the authentication plugin, forwarding configuration, firewall isolation, or a dedicated anti-DDoS service.
- It does not promise that every Minecraft version, auth plugin release, Fabric modpack or database topology works identically.
- Account switches do not automatically reconnect players. When needed, the player must reconnect manually to use the new login method.

## The three separate questions

1. **Admission:** may this player enter? For example, `offline-whitelist` rejects an unknown offline name.
2. **Identity:** has this connection proved a Java or Floodgate identity?
3. **Account access:** may that identity open this local authentication account without a password?

Passing one question does not answer the others. Start with [requirements](requirements.md), then read [login methods](../accounts/login.md).
