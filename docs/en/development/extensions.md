# Events and extension boundaries

Classic events use core interfaces and platform-specific implementations. Depend on the actual platform event class when registering a listener; a core interface is not itself a Bukkit `Event`. Folia has the mirrored Bukkit event classes with its own runtime threading constraints.

| Core contract | Purpose | Important boundary |
| --- | --- | --- |
| `FastLoginPreLoginEvent` | Observe username/source/profile before online-mode routing actions | Not authentication proof; no generic cancellable contract |
| `FastLoginAutoLoginEvent` | Intercept applicable forced auto-login | Cancellable; not a universal event for every registration/early AuthMe/Fabric path |
| `FastLoginPremiumToggleEvent` | Observe list-state changes and reason | A change notification, not a new trusted identity |
| `FastLoginAntiBotEvent` | Intercept a connection-limit rejection | Cancellation permits continuation; listeners can weaken admission protection |

Implementations live in each platform's `event` package. Events can fire asynchronously; follow the platform's threading and scheduling requirements when using its APIs.

## Integrating authentication

`AuthPlugin` and related interfaces expose account queries and login actions. Integrations must distinguish read failure from an absent account, check ownership and confirm the authentication plugin's action result.

Custom management tools should use authorized operations and preserve account-owner consent and password protection, rather than bypassing them with direct database edits.

## Protocol changes

Change the shared `protocol` contracts and test both Velocity and Fabric's generated copy. Validate origin, freshness, name/UUID/connection association, policy completeness and duplicate handling. A plugin message is not inherently authenticated because it used an FLP channel name. Preserve the established `fastloginplus:force` format unless a separately designed compatible migration is authorized.

Fabric's internal gate/bridge is not an advertised stable public event API. Do not promise parity with classic plugin events or wire out-of-tree mods to reflective internals without a defined compatibility contract.

Before changing an integration, see [build and contribute](build.md) and [login flow](../LOGIN-FLOW.md), and add or adjust tests for the behavior affected.
