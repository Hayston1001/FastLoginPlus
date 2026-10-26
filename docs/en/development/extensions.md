# Events and extension boundaries

Classic events use core interfaces and platform-specific implementations. Depend on the actual platform event class when registering a listener; a core interface is not itself a Bukkit `Event`. Folia has the mirrored Bukkit event classes with its own runtime threading constraints.

| Core contract | Purpose | Important boundary |
| --- | --- | --- |
| `FastLoginPreLoginEvent` | Observe username/source/profile before online-mode routing actions | Not authentication proof; no generic cancellable contract |
| `FastLoginAutoLoginEvent` | Intercept applicable forced auto-login | Cancellable; not a universal event for every registration/early AuthMe/Fabric path |
| `FastLoginPremiumToggleEvent` | Observe list-state changes and reason | A change notification, not a new trusted identity |
| `FastLoginAntiBotEvent` | Intercept a connection-limit rejection | Cancellation permits continuation; listeners can weaken admission protection |

Implementations live under each classic platform's `event` package. Read the actual firing call sites, not just interface names, to establish timing and thread. ProtocolLib workers and async proxy login paths are not Bukkit's main thread. Velocity asynchronous events must be completed without blocking the event loop. Folia actions need the proper region/entity/global scheduler.

## Integrating authentication

`AuthPlugin` and specialized pre-login contracts expose account queries and actions. An integration must distinguish read failure from account absence, enforce declared policy, check account ownership and verify save/action success. Do not report a failed `forceLogin` as success merely because session verification passed; a supported "already authenticated" path needs independent confirmation.

Do not bypass consent by directly updating a profile or writing the backend's nonexistent proxy-mode FLP database. A custom management UI must invoke authorized operations and preserve password/account safety, not treat setting `Premium=true` as equivalent to a complete login.

## Protocol changes

Change the shared `protocol` contracts and test both Velocity and Fabric's generated copy. Validate origin, freshness, name/UUID/connection association, policy completeness and duplicate handling. A plugin message is not inherently authenticated because it used an FLP channel name. Preserve the established `fastloginplus:force` format unless a separately designed compatible migration is authorized.

Fabric's internal gate/bridge is not an advertised stable public event API. Do not promise parity with classic plugin events or wire out-of-tree mods to reflective internals without a defined compatibility contract.

Regression tests should cover disconnect, reconnect, stale callbacks, duplicate requests, policy changes, failed reads/writes and shutdown/reload boundaries. See [build checks](build.md) and [login trust](../LOGIN-FLOW.md).
