# Architecture and module boundaries

| Module | Runtime floor | Responsibility |
| --- | --- | --- |
| `protocol` | 8 | Platform-free identity/account message contracts |
| `shared` | 8 | Dependency-free config I/O, language/fallback and small utilities |
| `craftapi` | 8 | Vendored Mojang client, UUID encoding/lookup/session operations |
| `core` | 8 | Classic shared routing, records, auth interfaces, anti-bot and relay models |
| `bukkit` | 8 | Spigot/Paper packet/early-login integration and backend auth execution |
| `folia` | 21 | Hand-maintained Bukkit mirror adapted to region scheduling |
| `bungee` | 17 | Bungee identity, list persistence, forwarding and optional BungeeAuth hook |
| `velocity` | 17 | Velocity identity, list persistence and forwarded declarations |
| `fabric26_2` (`fabric/mc26_2`) | 25 | FabricProxy-Lite-forwarded identity and EasyAuth account decisions |

These are plugin bytecode floors; the host software can require higher Java. All default builds include Fabric and run Gradle on JDK 25.

## Proxy and backend

The proxy decides verification routing, establishes Mojang/Floodgate identity, enforces network admission, owns FLP records and performs proxy-side writes/forwarding. The backend reads local auth-account state and makes the final auth-plugin action decision under the proxy's declared policies, then performs registration/login or leaves the password lock/rejects.

Neither side can replace the other's authority. The backend must not reconstruct a second identity verdict from a name, UUID shape or independent Floodgate check after consuming a valid declaration. The proxy must not claim account access succeeded before the local auth action is confirmed.

## Source boundaries

`core` does not import platform APIs, and `craftapi` does not own FLP business rules. Fabric uses `protocol` and `shared` sources rather than depending on Bukkit/Folia or proxy plugin JARs.

Bukkit and Folia have separate source files with different scheduling and platform adaptations. Check both when changing shared behavior; do not copy Bukkit sources over Folia.

## Persistence boundaries

Proxy FLP records are the source of the network list; backend AuthMe/EasyAuth stores hold local accounts. Updates to the two stores are not one database transaction, so success in one does not imply success in the other.

See also [login flow](../LOGIN-FLOW.md) and [events and extensions](extensions.md).
