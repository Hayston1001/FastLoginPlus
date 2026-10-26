# Skins and SkinsRestorer

`forwardSkin: true` allows FLP to carry a verified Java profile's textures to the server. The Mojang `textures` property can contain both skin and cape data, so disabling it affects both, not just the skin image.

On a standalone server FLP uses its Bukkit/Paper-specific application path. Behind a proxy, the **proxy's** `forwardSkin` controls the forwarded profile; changing the backend copy does not override it. Other plugins or server profile caches may still supply a skin, so `forwardSkin: false` is not a universal "nobody has a skin" switch.

## SkinsRestorer

Install SkinsRestorer only on supported platform/version combinations according to its own guide. FLP's compatibility path checks for a custom skin and avoids overwriting it. When connection UUIDs are offline, identity-sensitive lookups must still use the verified Mojang identity where appropriate, rather than assuming the connection UUID is Mojang's UUID.

To diagnose a conflict:

1. Record server, FLP, proxy and SkinsRestorer versions and whether the player has an explicit custom skin.
2. Check `forwardSkin` on the identity-handling side and the selected `premiumUuid` policy.
3. Test a reconnect with one known custom skin and one default premium skin in staging.
4. Inspect debug logs for compatibility API failures and profile-cache effects before clearing data.

A visible premium skin is not an authentication result. This classic integration does not promise a SkinsRestorer API implementation inside the Fabric mod or compatibility with every skin plugin.
