# BungeeCord and classic backends

This guide covers BungeeCord with Bukkit/Paper/Folia authentication backends. It is not a Fabric deployment guide. A Bungee-compatible fork still needs testing against its actual API and forwarding implementation.

## Install and configure

1. Back up the proxy, backends, FLP records and authentication databases. Stop the network.
2. Put **BungeeCord** FLP on the proxy. Install **Bukkit** or **Folia** FLP, as appropriate, and the auth plugin on every participating backend. FLP does not require backend ProtocolLib in proxy mode.
3. In BungeeCord's `config.yml`, set `online_mode: false` and `ip_forward: true`. FLP requests Mojang authentication for individual connections.
4. Set backend `server.properties` to `online-mode=false` and `spigot.yml` to `settings.bungeecord: true`. Disable Velocity modern forwarding on Paper. On Paper, set the BungeeCord forwarding online-mode setting to match the proxy's offline setting, using your Paper version's configuration reference.
5. Start the proxy to generate its instance UUID. Use the **`stats` UUID in BungeeCord's own `config.yml`**, not Velocity's `proxyId.txt`. Add it to each backend's `plugins/fastloginplus/allowed-proxies.txt`, one per line, then restart the backends. Do not delete or regenerate the proxy UUID casually.
6. Configure FLP policy and database on the proxy. Configure the auth plugin and local messages on the backend.
7. Firewall backend ports so only trusted proxies can reach them. Test [valid and invalid login paths](verification.md).

## Security boundary

BungeeCord's legacy forwarding does not authenticate the full forwarded identity. FLP's current signed declarations add validation for the FLP decisions, but neither the UUID allowlist nor a profile property makes a public backend safe. A party controlling the proxy or trusted backend plugins is inside the trust boundary. Proxy IDs are also carried in plugin messages; treat them as sensitive, not as a firewall replacement.

Leave `verify-backend-messages: true` in the proxy's FLP config. Temporarily disabling it for an old deployment reduces message checking; it is not a fix for an incorrect allowlist.

## Commands and storage

Run `/flp` commands in game on a backend or from that backend's console. FLP does not register a native BungeeCord `/flp` command. A backend command can need an online player to carry its message; classic backends persist pending requests when no carrier is available.

The proxy database holds the network's FLP premium/cracked records, while the backend auth database holds passwords and local account state. The optional BungeeAuth hook is a separate integration, not a reason to install AuthMe's backend JAR on the proxy.

See the official [IP forwarding guide](https://www.spigotmc.org/wiki/bungeecord-ip-forwarding/) and [commands reference](../accounts/commands.md).
