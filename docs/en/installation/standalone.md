# Standalone Spigot/Paper

Use this guide only when players connect directly to this server. Behind a proxy, use the [Velocity](velocity.md) or [BungeeCord](bungeecord.md) guide instead.

## Before installing

- Check [Java and dependency requirements](../overview/requirements.md).
- Choose one [supported auth plugin](../integrations/auth-plugins.md). AuthMe has a [dedicated integration guide](../integrations/authme.md).
- Obtain a ProtocolLib build compatible with the exact Minecraft/server version. ProtocolSupport is an alternative legacy integration; do not assume it covers every modern release.
- Back up the world, player data, auth database and any existing FLP data. Decide whether players should keep their existing UUIDs before first login.

## Install

### 1. Install the plugins

Stop the server and put these plugins in `plugins/`:

| Plugin | Choose |
| --- | --- |
| FastLoginPlus | The **Bukkit** edition, not `-plain.jar` |
| Authentication plugin | A version compatible with your server, such as AuthMe |
| ProtocolLib | A version compatible with your Minecraft and server version |

### 2. Configure the server

Set `online-mode=false` in `server.properties`. This allows different login methods, but unverified players must still authenticate through the authentication plugin.

Once you have confirmed that players connect directly to this server, set `settings.bungeecord: false` in `spigot.yml`. On Paper, disable Velocity forwarding in Paper's configuration as well.

### 3. Configure FLP

1. Start the server once, check that the plugins load, then stop it.
2. Open the generated `plugins/fastloginplus/config.yml`. Use the folder name actually generated on your server.
3. Review `premiumUuid`. The default is `true`; an existing offline server may need `false` to preserve its connection UUIDs. Read [UUIDs and player data](../accounts/uuid.md) before changing it.
4. Adjust `autoRegister`, `autoLogin`, `auto-register-unknown` and `offline-whitelist` if needed. See [Java login policies](../configuration/java.md) for their purposes.
5. Set `language` to `en` or `zh`, then restart the server.

## Verify

Check the startup log for plugin loading errors, then use test accounts to check premium login and an existing password account. An existing password account still requires its password until its owner agrees to bind it. For login dialog problems, see [troubleshooting](../operations/troubleshooting.md).

Before admitting public players, keep a password recovery method and an operator account available.
