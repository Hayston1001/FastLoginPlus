# Commands and permissions

## Where commands run

Use `/flp` on a **Bukkit/Folia or Fabric backend**, in game or its console where supported. Current Velocity/BungeeCord plugins do not register a native `/flp` command. In proxy deployments the backend relays list changes to the proxy; it does not write a second FLP list.

## Bukkit and Folia

Replace `<prefix>` below with `fastloginplus.bukkit.command` on Bukkit/Paper or `fastloginplus.folia.command` on Folia.

| Command | Permission | Shipped default / effect |
| --- | --- | --- |
| `/flp premium` | `<prefix>.premium` | Everyone; request own premium route, with configured confirmation |
| `/flp premium <player>` | `<prefix>.premium.other` | Operator by default; authorize another name |
| `/flp cracked` | `<prefix>.cracked` | Operator; switch own name to cracked |
| `/flp cracked <player>` | `<prefix>.cracked.other` | Operator by default; change another name |
| `/flp delete <player>` | `<prefix>.delete` | Operator; delete a non-premium FLP record |
| `/flp` | Server operator check | Version and usage |

There is **no implemented `delete.other` requirement**. Delete always takes a name and checks its base permission. The premium/cracked `.other` nodes include their base permission in the descriptor. Actual grants can be changed by your permissions plugin.

Classic aliases are `prem`, `unpremium` and `del`. `/premium` is not an FLP alias. Self premium/cracked operations require a player; a console must supply the target name. Names in examples are literal targets, not UUIDs.

In proxy mode, a Bukkit/Folia backend needs an online player to send account changes to the proxy. When it is empty, requests are saved in `pending-relay.json` until a player joins that backend; restarting does not clear them. Sending a request does not mean it has completed. Check the final feedback before repeating an operation.

## Fabric

| Command | Requirement |
| --- | --- |
| `/flp premium` | Player authenticated to a consistent EasyAuth password account |
| `/flp premium <player>` | Vanilla gamemaster/operator command permission |
| `/flp cracked` | Gamemaster/operator permission plus authenticated self account |
| `/flp cracked <player>` | Gamemaster/operator command permission |
| `/flp delete <player>` | Gamemaster/operator command permission; local account cannot still be bound |

Fabric does not use Bukkit permission nodes or classic aliases. No bare version/help handler or `/flp reload` is registered there. Console other-player commands require an online player to carry the message; an empty server reports failure, not a durable queued operation. Final proxy results are distinct from immediate backend hand-off feedback.

## Destructive and delayed effects

Read [binding and switching](binding.md) before granting permissions. AuthMe cracked cleanup can remove/reset an auth account. EasyAuth unbinding retains an existing password; a passwordless release requires a second operation within 30 seconds. FLP delete does not delete the world or all authentication records.

`kick-toggle` affects disconnect behavior, not authentication proof. No command transfers or automatically reconnects the player. Operators should validate target names, confirm final writes and test the next login.
