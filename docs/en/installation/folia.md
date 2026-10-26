# Standalone Folia

Folia uses region-based threading. A plugin being compatible with Paper does not make it Folia-compatible.

## Install

1. Back up the server and authentication data. Check the [runtime requirements](../overview/requirements.md).
2. Stop the server. Install the **Folia** FLP artifact in `plugins/`, not the Bukkit artifact. Install Folia-compatible builds of your auth plugin and packet library as well.
3. Set `online-mode=false` in `server.properties`. For a direct server, disable BungeeCord and Velocity forwarding.
4. Start once, confirm that all dependencies enabled successfully, then stop. Edit `plugins/fastloginplus/config.yml` using the [standalone setup choices](standalone.md), including the UUID decision.
5. Restart fully and run the [verification checklist](verification.md). Look for unsupported scheduler calls in both FLP and its dependencies.

The configuration template is shared with Bukkit, but the scheduling implementation is not. Folia permissions use `fastloginplus.folia.command.*`, not `fastloginplus.bukkit.command.*`.

## Behind a proxy

Use the [Velocity](velocity.md) or [BungeeCord](bungeecord.md) guide, installing the Folia artifact on the backend. FLP does not use ProtocolLib interception there. Every other plugin must still meet Folia's threading requirements.

Do not use plugin hot-loaders or generic reload commands as an installation procedure. A clean startup and actual client tests are required; successful JAR loading alone does not prove that login callbacks run on the correct threads.

See [Folia's official documentation](https://docs.papermc.io/folia/) for its plugin compatibility and scheduling model.
