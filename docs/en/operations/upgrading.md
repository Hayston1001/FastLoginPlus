# Backup, upgrade and rollback

This manual targets unreleased 1.0.0. Do not assume a JAR labeled 0.7.0 contains all these behaviors; identify development artifacts by commit. Read the [release notes](https://github.com/Hayston1001/FastLoginPlus/releases) for the actual build being installed.

## Back up a consistent set

Stop affected processes or use each database's supported consistent-backup procedure. Preserve:

- FLP config, languages and SQLite database, or a consistent MySQL/MariaDB dump.
- AuthMe/EasyAuth/other auth-plugin data, config and databases.
- Velocity `proxyId.txt`, BungeeCord's `stats` UUID, backend `allowed-proxies.txt` / Fabric `allowed-proxy-ids`, and forwarding secrets in a secure backup.
- Classic `pending-relay.json`, since undelivered admin requests survive restarts.
- Worlds/player data and other plugins' UUID-indexed records, plus old JARs and exact versions.

Test restoring the backup in an isolated environment. A copy of FLP's database alone is not an account or inventory recovery plan.

## Upgrade procedure

1. Reproduce the network in staging and note current player UUID policy, authentication methods and existing account types.
2. Stop affected proxy/backends, install matching platform artifacts, and preserve data/config/trust identities. Do not deploy `-plain.jar` or several FLP versions together.
3. Prefer updating both sides in one maintenance window. During an older rolling upgrade, backend message stamping and declared-policy compatibility matter: old unstamped backends require backend upgrades before enforcing proxy checks; do not enable strict declared-policy requirements until proxies send those facts. There is no universal safe order for every mixed generation.
4. Start, inspect configuration/language migration warnings, trust lists, database errors and auth integration takeover. Existing configuration values normally survive; a new default does not silently replace them all.
5. Verify premium, password, whitelist, switch, reconnect and failure paths using the [checklist](../installation/verification.md). Confirm final database outcomes, not only successful startup.
6. Reopen to players only when exact supported combinations are checked. Keep a recovery operator available.

Removed `anti-bot.action`, old Fabric `.properties` instructions, transfer/cookie binding descriptions and old one-shot fallback semantics must not be copied into new configurations. Revisit the [policy reference](../configuration/java.md).

## Rollback

Stop writes before reverting. Older plugins can interpret new schema/account states incorrectly, so do not merely swap JARs after account conversion. Restore a compatible, coordinated backup of FLP and auth state and assess player-data changes separately. Commands still queued at rollback time can later reapply old admin intent.

Never regenerate a proxy ID or clear a database as a routine rollback step. If a backup is unavailable or stores disagree, preserve the evidence and reconcile account ownership under operator control rather than allowing all logins.
