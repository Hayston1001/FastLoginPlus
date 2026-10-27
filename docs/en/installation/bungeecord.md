# BungeeCord with Spigot/Paper/Folia

This guide covers BungeeCord with Bukkit/Paper/Folia authentication backends. It is not a Fabric deployment guide. A Bungee-compatible fork still needs testing against its actual API and forwarding implementation.

## Before you begin

Check the [requirements](../overview/requirements.md), choose a compatible [authentication plugin](../integrations/auth-plugins.md), and back up the proxy, backends and their databases. Stop all affected servers before installation.

## Installation

{% stepper %}
{% step %}
### Install the plugins

| Location | Plugins to put in `plugins/` |
| --- | --- |
| BungeeCord proxy | FLP **BungeeCord** edition |
| Each Spigot/Paper backend | FLP **Bukkit** edition and an authentication plugin |
| Each Folia backend | FLP **Folia** edition and a Folia-compatible authentication plugin |

FLP does not need backend ProtocolLib in this setup. Do not use `-plain.jar`.

{% endstep %}
{% step %}
### Configure the proxy and backends

In BungeeCord's `config.yml`, set:

```yaml
online_mode: false
ip_forward: true
```

FLP verifies premium players when needed. On each backend, set `online-mode=false` in `server.properties` and set the following in `spigot.yml`:

```yaml
settings:
  bungeecord: true
```

On Paper or Folia, also disable Velocity forwarding and set the BungeeCord forwarding online-mode option to `false`. The location of these options varies by Paper version; consult your version's configuration reference.

{% endstep %}
{% step %}
### Allow the backend to trust the proxy

1. Start the proxy and backends once to generate configuration files, then stop both sides.
2. In BungeeCord's own `config.yml`, copy the UUID in `stats`. This is not Velocity's `proxyId.txt`.
3. Add the UUID to each backend's `plugins/fastloginplus/allowed-proxies.txt`, one UUID per line. Preserve the proxy's `stats` value rather than regenerating it.

{% endstep %}
{% step %}
### Configure policies and start

1. Configure FLP login policies and database in the proxy's `plugins/fastloginplus/config.yml`.
2. Configure the authentication plugin and backend language on the backend.
3. Firewall backend ports so only trusted proxies can connect.
4. Start the proxy and backends, check that plugins load, and try premium and password login with test accounts.

{% endstep %}
{% endstepper %}

## Security boundary

{% hint style="warning" %}
BungeeCord forwarding alone does not secure the entire backend. Even with an FLP proxy allowlist, use a firewall to prevent players connecting directly to the backend. Keep proxy IDs private.
{% endhint %}

Leave `verify-backend-messages: true` in the proxy's FLP config. Temporarily disabling it for an old deployment reduces message checking; it is not a fix for an incorrect allowlist.

## Commands and storage

Run `/flp` commands in game on a backend or from its console, not the BungeeCord console. When the backend is empty, some requests wait for a player to join before they are sent. Check the final feedback.

The proxy database holds the network's FLP premium/cracked records, while the backend auth database holds passwords and local account state. The optional BungeeAuth hook is a separate integration, not a reason to install AuthMe's backend JAR on the proxy.

See the official [IP forwarding guide](https://www.spigotmc.org/wiki/bungeecord-ip-forwarding/) and [commands reference](../accounts/commands.md).
