# Standalone Folia

Folia uses region-based threading. A plugin being compatible with Paper does not make it Folia-compatible.

## Install

{% stepper %}
{% step %}
### Back up and check requirements

Back up the server and authentication data. Check the [runtime requirements](../overview/requirements.md).
{% endstep %}

{% step %}
### Install the plugins

Stop the server. Install the **Folia** FLP artifact in `plugins/`, not the Bukkit artifact. Install Folia-compatible builds of your auth plugin and packet library as well.
{% endstep %}

{% step %}
### Configure the server

Set `online-mode=false` in `server.properties`. For a direct server, disable BungeeCord and Velocity forwarding.
{% endstep %}

{% step %}
### Configure FLP

Start once, confirm that all dependencies enabled successfully, then stop. Edit `plugins/fastloginplus/config.yml` using the [standalone setup choices](standalone.md), including the UUID decision.
{% endstep %}

{% step %}
### Restart and check

Restart the server, check that the plugins load, and try premium and password login with test accounts.
{% endstep %}
{% endstepper %}

Folia permissions use `fastloginplus.folia.command.*`, not `fastloginplus.bukkit.command.*`.

## Behind a proxy

Use the [Velocity](velocity.md) or [BungeeCord](bungeecord.md) guide, installing the Folia edition on the backend. FLP does not need backend ProtocolLib in this setup, but other plugins must still support Folia.

Restart fully after installation or configuration changes; do not use plugin hot-loaders or generic reload commands.

See [Folia's official documentation](https://docs.papermc.io/folia/) for its plugin compatibility and scheduling model.
