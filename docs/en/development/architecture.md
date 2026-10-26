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

## Responsibility is not "proxy decides, backend executes"

The proxy decides verification routing, establishes Mojang/Floodgate identity, enforces network admission, owns FLP records and performs proxy-side writes/forwarding. The backend reads local auth-account state and makes the final auth-plugin action decision under the proxy's declared policies, then performs registration/login or leaves the password lock/rejects.

Neither side can replace the other's authority. The backend must not reconstruct a second identity verdict from a name, UUID shape or independent Floodgate check after consuming a valid declaration. The proxy must not claim account access succeeded before the local auth action is confirmed.

## Source boundaries

`core` does not import platform APIs. `craftapi` does not own FLP business rules. `shared` has no production dependencies or bundled resources; callers supply parsers, resources and policy. Fabric consumes generated source copies of `protocol`/`shared` in its own namespace, not the classic platform classes or JARs. Its artifact must not contain old `com.github.games647.fastlogin` references.

Bukkit/Folia sources keep the existing `com.github.games647.fastlogin.bukkit` compatibility package. They are distinct files, with intentional scheduler/platform differences. Do not bulk-copy over Folia. Bungee implementation internals are accessed through the established reflection boundary, not static linkage to unreleased classes.

## Persistence boundaries

Proxy FLP records are the network list authority; backend AuthMe/EasyAuth stores are local account authority. Protocol/profile facts, pending requests and account updates have different lifetimes. Per-name locking avoids some classic load/modify/save races but is not a distributed transaction or a global multi-proxy lock.

The current default build has no `web` module. A WebUI feature branch is not a shipped 1.0.0 capability unless explicitly ported, built and accepted. Refer to [login flow](../LOGIN-FLOW.md), [extension boundaries](extensions.md) and the [shared module note](https://github.com/Hayston1001/FastLoginPlus/blob/main/shared/README.md).
