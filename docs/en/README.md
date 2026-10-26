# FastLoginPlus Wiki

> This manual describes the forthcoming **1.0.0**, which has not been released. It documents the current implementation, not just changes since 0.7.0. An older release may not contain the features described here. Development builds can still carry the previous version number; identify them by commit as well.

FastLoginPlus (FLP) connects verified Minecraft identities with your server's authentication system. It can let verified Java account owners and supported Floodgate players log in without entering a password, while keeping password authentication for other players. Whether an account can be created or opened depends on server policy and the local account state.

## Where to start

- New to FLP: [what it does](overview/introduction.md) and [choose your deployment](overview/requirements.md).
- Installing: [Spigot/Paper](installation/standalone.md), [Folia](installation/folia.md), [Velocity](installation/velocity.md), [BungeeCord](installation/bungeecord.md), or [Fabric/EasyAuth](installation/fabric.md).
- Managing players: [login methods](accounts/login.md), [lists and admission](accounts/lists.md), [binding and switching](accounts/binding.md), and [commands](accounts/commands.md).
- Configuring: [files and scope](configuration/README.md). Editing a backend copy of a proxy-owned option does not change network policy.
- Having trouble: [troubleshooting](operations/troubleshooting.md) and [reporting a problem](operations/reporting.md).
- Developing: [build and test](development/build.md) and [architecture](development/architecture.md).

## Read these warnings first

1. A premium username is not proof of ownership. The current connection must pass Mojang verification.
2. FLP's premium/cracked list is not the authentication plugin's account database.
3. Changing the UUID delivered to the server can change which inventory, permissions and other player data are loaded. FLP does not migrate that data.
4. A proxy allowlist does not replace firewall rules or authenticated forwarding. Keep backend ports inaccessible to the public.

The English and Chinese manuals have the same structure. Use GitBook's language selector on the published site. On GitHub, open the [Chinese manual](https://github.com/Hayston1001/FastLoginPlus/tree/main/docs/zh).

[Downloads and releases](https://github.com/Hayston1001/FastLoginPlus/releases) | [Changelog](https://github.com/Hayston1001/FastLoginPlus/blob/main/CHANGELOG.md) | [Issues](https://github.com/Hayston1001/FastLoginPlus/issues) | [Source](https://github.com/Hayston1001/FastLoginPlus)
