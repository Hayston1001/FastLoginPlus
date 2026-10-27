# Velocity with Spigot/Paper/Folia

This guide covers Velocity with Spigot, Paper or Folia servers. For a Fabric backend, use the [Fabric guide](fabric.md).

## Before you begin

- Check the [requirements](../overview/requirements.md) and choose a compatible [authentication plugin](../integrations/auth-plugins.md).
- Back up proxy and backend data, including the FLP and authentication databases. Stop all affected servers before installation.
- Modern forwarding is recommended for Paper and Folia. Spigot does not support it; use the legacy instructions below.

## Paper and Folia: modern forwarding

{% stepper %}
{% step %}
### Install the plugins

| Location | Plugins to put in `plugins/` |
| --- | --- |
| Velocity proxy | FLP **Velocity** edition |
| Each Paper backend | FLP **Bukkit** edition and an authentication plugin |
| Each Folia backend | FLP **Folia** edition and a Folia-compatible authentication plugin |

FLP does not need ProtocolLib in this setup. Do not use `-plain.jar`.

{% endstep %}
{% step %}
### Configure Velocity

Edit the proxy's `velocity.toml`:

```toml
online-mode = false
player-info-forwarding-mode = "modern"
```

FLP verifies premium players when needed; the entire proxy does not need to be in online mode.

{% endstep %}
{% step %}
### Configure each backend

In `server.properties`, set:

```properties
online-mode=false
```

In `spigot.yml`, set:

```yaml
settings:
  bungeecord: false
```

Find `proxies.velocity` in `config/paper-global.yml` and set the following values. Replace `secret` with the contents of the proxy's `forwarding.secret` file:

```yaml
proxies:
  velocity:
    enabled: true
    online-mode: false
    secret: "contents of forwarding.secret"
```

Edit existing fields rather than adding a second `proxies` section. Older Paper versions may use `paper.yml`; consult the documentation for your version.

{% endstep %}
{% step %}
### Allow the backend to trust the proxy

1. Start the proxy and backends once to generate FLP files, then stop them.
2. Copy the UUID from the proxy's `plugins/fastloginplus/proxyId.txt`.
3. Add that UUID to each backend's `plugins/fastloginplus/allowed-proxies.txt`, one UUID per line.

{% hint style="warning" %}
The proxy ID and forwarding secret are different values and cannot be exchanged. Keep both private.
{% endhint %}

{% endstep %}
{% step %}
### Configure policies and start

1. Set Java/Bedrock login policies and the FLP database in the proxy's `plugins/fastloginplus/config.yml`.
2. Configure the authentication plugin and backend language on each backend. Backend copies of proxy-owned settings do not override the proxy's policy.
3. Firewall the backend ports so only trusted proxies can connect.
4. Start the proxy and backends, check that plugins load, and try premium and password login with test accounts.

{% endstep %}
{% endstepper %}

{% hint style="info" %}
An existing password account may still require its password until its owner agrees to bind it. That does not necessarily indicate an installation problem. Run account commands on the backend, not the Velocity console.
{% endhint %}

## Spigot: legacy forwarding

Spigot does not support modern forwarding. Install the plugins and configure the proxy allowlist as above, but use these forwarding settings:

1. In the proxy's `velocity.toml`, set `online-mode = false` and `player-info-forwarding-mode = "legacy"`.
2. On each backend, set `online-mode=false` in `server.properties` and `settings.bungeecord: true` in `spigot.yml`.
3. If using legacy mode with Paper, disable Paper's Velocity modern forwarding. Do not enable both forwarding modes.
4. Restrict backend ports to trusted proxies, then restart and check login.

{% hint style="warning" %}
Legacy forwarding does not authenticate its source, so backend isolation is essential. Prefer modern forwarding with Paper/Folia where possible.
{% endhint %}

See the [Velocity forwarding](https://docs.papermc.io/velocity/player-information-forwarding/) and [security](https://docs.papermc.io/velocity/security/) guides.
