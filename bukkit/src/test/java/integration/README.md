# Authentication integration testing

This directory currently contains documentation only, not an executable server/client integration suite. A successful Gradle test run must not be described as a real Minecraft login acceptance test on that basis.

## What automated tests cover

Current module tests exercise account decisions, packet helpers, persistence and simulated platform interactions under their own test packages. Run the affected module tasks and inspect their reports. They do not automatically launch a public server, authenticate a paid account or validate every external plugin version.

## Real integration evidence

Use the [installation verification checklist](https://github.com/Hayston1001/FastLoginPlus/blob/main/docs/en/installation/verification.md) for a staging server. Record:

- Exact FLP commit, server/proxy, auth plugin, packet library and Java versions.
- Deployment, forwarding/trust setup, configuration and account preconditions.
- Fresh/returning/password/bound identities and negative ownership cases.
- Reconnect, disconnect during callbacks, duplicate requests and changed policy.
- Failed account/FLP reads and writes, Mojang outages, restart and recovery.
- Persistent account/list state as well as player-visible outcomes.

Test credentials, forwarding secrets, proxy IDs, private keys, server data and personal information must not be committed. Use disposable accounts and consistent backups. Distinguish mocked tests, compilation, startup, actual client login and fault injection in reports.

If an automated container/client suite is introduced later, document its prerequisites, invoked task, external access, secrets and reproducible isolation here. Historical ideas about Spigot containers, MCProtocolLib or CRIU are not an existing supported test runner.
