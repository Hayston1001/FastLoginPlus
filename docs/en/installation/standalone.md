# Standalone Spigot/Paper

Use this guide only when players connect directly to this server. Behind a proxy, use the [Velocity](velocity.md) or [BungeeCord](bungeecord.md) guide instead.

## Before installing

- Check [Java and dependency requirements](../overview/requirements.md).
- Choose one [supported auth plugin](../integrations/auth-plugins.md). AuthMe has a [dedicated integration guide](../integrations/authme.md).
- Obtain a ProtocolLib build compatible with the exact Minecraft/server version. ProtocolSupport is an alternative legacy integration; do not assume it covers every modern release.
- Back up the world, player data, auth database and any existing FLP data. Decide whether players should keep their existing UUIDs before first login.

## Install

1. Stop the server. Put the installable **Bukkit** FLP JAR, auth plugin and packet library in `plugins/`. Do not use the `-plain.jar`, Folia, proxy or Fabric artifact.
2. Set `online-mode=false` in `server.properties`. This allows mixed login methods; password authentication must remain enabled for unverified players.
3. Disable proxy forwarding if this is genuinely a direct server: `settings.bungeecord: false` in `spigot.yml` and Velocity support disabled in Paper's configuration.
4. Start once to generate files, check startup errors, then stop before editing. FLP's backend files are under `plugins/fastloginplus/`; inspect the generated folder rather than creating a second differently capitalized folder.
5. Review `config.yml`, especially `premiumUuid`, `autoRegister`, `autoLogin`, `auto-register-unknown` and `offline-whitelist`. The shipped `premiumUuid` default is `true`; an existing offline server may need `false` to preserve connection UUIDs. See [UUID risks](../accounts/uuid.md).
6. Select `language: en` or `language: zh`. Restart the server completely after configuration changes.

## Verify

Check that FLP found the auth plugin and registered the packet path without an encryption-response mapping warning. Test a fresh verified Java account, a password account, a reconnect and an unverified connection. An account's existing password/ownership can legitimately prevent automatic login; a skin or a premium-looking name is not proof that authentication succeeded.

On Paper, AuthMe 6 pre-join handling may run earlier than join-time plugin actions. A blocked dialog is not fixed by repeatedly changing `autoRegister`; follow [AuthMe troubleshooting](../operations/troubleshooting.md).

Complete the [verification checklist](verification.md) before admitting public players. Keep a password recovery method and an operator account available.
