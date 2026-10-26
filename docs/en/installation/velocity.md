# Velocity and classic backends

Install FLP on both the proxy and every backend where FLP participates in authentication. Run account commands on the backend, not the Velocity console. Fabric has a [separate guide](fabric.md).

## Recommended: modern forwarding to Paper/Folia

1. Stop the proxy and backends; back up FLP and auth-plugin data.
2. Put the **Velocity** FLP artifact in the proxy's `plugins/`. Put **Bukkit** FLP on Paper or **Folia** FLP on Folia, with the backend auth plugin. ProtocolLib is not needed by FLP in this deployment.
3. Configure `velocity.toml`:

   ```toml
   online-mode = false
   player-info-forwarding-mode = "modern"
   ```

   FLP requests online-mode verification per connection. Do not set the proxy globally online merely because some players are premium.
4. On each backend, set `server.properties` to `online-mode=false`. Set `settings.bungeecord: false` in `spigot.yml`.
5. In `config/paper-global.yml`, enable `proxies.velocity.enabled`, set `proxies.velocity.online-mode: false`, and set `proxies.velocity.secret` to the contents of Velocity's `forwarding.secret`. Older Paper versions use `paper.yml`; follow the server version's documentation rather than creating both configurations.
6. Start once to generate FLP files. Copy the UUID from the proxy's `plugins/fastloginplus/proxyId.txt` into each backend's `plugins/fastloginplus/allowed-proxies.txt`, one UUID per line. Stop and restart each backend after editing it.
7. Configure Java/Bedrock policy and the FLP database on the **proxy**. Configure backend language and auth-plugin settings on the **backend**. Do not share the auth database as a replacement for the proxy's FLP list.
8. Restrict backend ports to the proxy and perform the [verification tests](verification.md).

The forwarding secret and FLP proxy ID are different values with different roles. Do not copy one into the other's file. Protect both, and do not post them in reports.

## Spigot or legacy forwarding

Plain Spigot does not support Velocity modern forwarding natively. If using Velocity's `legacy` mode, set `settings.bungeecord: true` on the backend, disable Paper Velocity modern forwarding, and keep `online-mode=false` on proxy and backend. Use the same FLP proxy ID allowlist as above.

Legacy forwarding is not authenticated by the forwarding transport. Current FLP validates signed identity declarations for its own premium admission, but this does not secure the entire Minecraft login or other plugins. Firewall/backend isolation is mandatory. Do not enable both forwarding modes together. Prefer modern forwarding where possible; this guide does not certify arbitrary forwarding add-ons.

## Check the result

- Backend startup reports proxy mode and a nonempty trusted proxy list; no FLP standalone packet interception is needed.
- The proxy owns FLP records. The backend still reads its local auth accounts and decides account actions using the proxy's declared policy.
- A verified account with an existing unconsented password account can still need its password. That is not a forwarding failure.
- A command hand-off is not a completed database change. Check final feedback and reconnect behavior.

Authoritative platform setup: [Velocity forwarding](https://docs.papermc.io/velocity/player-information-forwarding/) and [network security](https://docs.papermc.io/velocity/security/).
