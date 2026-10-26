# Requirements and deployment choices

## Choose the path

| Deployment | Install FLP on | Authentication account lives on |
| --- | --- | --- |
| Standalone Spigot/Paper | Bukkit backend | Backend auth plugin |
| Standalone Folia | Folia backend | Folia-compatible auth plugin |
| Velocity + classic backends | Velocity and each participating Bukkit/Folia backend | Backend auth plugin |
| BungeeCord + classic backends | BungeeCord and each participating Bukkit/Folia backend | Backend auth plugin, or the separately supported BungeeAuth hook |
| Velocity + Fabric 26.2 | Velocity and Fabric backend | EasyAuth on Fabric |

For a new Paper/Folia proxy network, prefer Velocity modern forwarding. Plain Spigot does not natively support modern forwarding; use the [legacy branch of the Velocity guide](../installation/velocity.md) or BungeeCord with backend isolation. Fabric requires modern forwarding through FabricProxy-Lite, not BungeeCord.

## Java requirements

| FLP artifact | Plugin bytecode minimum | Additional requirements |
| --- | --- | --- |
| Bukkit | Java 8 | Server, authentication plugin and packet library may require newer Java |
| Folia | Java 21 | A compatible Folia server and Folia-compatible dependencies |
| BungeeCord | Java 17 | Proxy's own requirement also applies |
| Velocity | Java 17 | Proxy's own requirement also applies |
| Fabric 26.2 | Java 25 | Minecraft 26.2, Fabric Loader >=0.19.3, Fabric API, FabricProxy-Lite, EasyAuth |

Run the highest Java version required by the whole installation. A bytecode minimum does not promise that a modern Paper or ProtocolLib release runs on Java 8. Building FLP is separate: Gradle runs on JDK 25.

## Dependencies and support limits

- Standalone Bukkit/Folia needs a compatible packet library and authentication plugin. FLP compiles against ProtocolLib 5.3.0; use a ProtocolLib build compatible with your exact server, not merely a version numerically above 5.3.0. ProtocolSupport is an alternate compatibility path, not a dependency of proxy backends.
- Proxy backends do not use FLP's ProtocolLib interception. The proxy handles the online-mode handshake. Other plugins may still need ProtocolLib themselves.
- AuthMe 5.x and 6.x have distinct integration behavior. Presence of another auth hook is not proof of AuthMe-style cleanup or pre-join support.
- Fabric's reference integration is EasyAuth 3.4.4. The loader accepts `>=3.4.0 <3.5.0`; other patch builds are not a blanket runtime guarantee. Use local SQLite for the supported Fabric storage combination; other EasyAuth stores are unverified.
- Geyser/Floodgate and every auth plugin have independent server and Java requirements. Check their own documentation.

The artifact list is not a tested matrix of every Minecraft release. Verify your exact combination using the [installation checklist](../installation/verification.md) before opening it to players. Fabric support does not extend automatically to other Minecraft versions, linked Bedrock accounts or multiple-backend account migration.
