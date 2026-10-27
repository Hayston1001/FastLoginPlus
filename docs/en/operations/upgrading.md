# Backup, upgrade and rollback

This manual describes FLP 1.0.0. Older release artifacts do not contain all documented behaviors. Read the [release notes](https://github.com/Hayston1001/FastLoginPlus/releases) for the actual build being installed.

## Back up a consistent set

Stop affected processes or use each database's supported consistent-backup procedure. Preserve:

- FLP config, languages and SQLite database, or a consistent MySQL/MariaDB dump.
- AuthMe/EasyAuth/other auth-plugin data, config and databases.
- Velocity `proxyId.txt`, BungeeCord's `stats` UUID, backend `allowed-proxies.txt` / Fabric `allowed-proxy-ids`, and forwarding secrets in a secure backup.
- Bukkit/Folia backend `pending-relay.json`, since undelivered admin requests survive restarts.
- Worlds/player data and other plugins' UUID-indexed records, plus old JARs and exact versions.

Test restoring the backup in an isolated environment. A copy of FLP's database alone is not an account or inventory recovery plan.

## Upgrade procedure

1. Reproduce the network in staging and note current player UUID policy, authentication methods and existing account types.
2. Stop affected proxy/backends, install matching platform artifacts, and preserve data/config/trust identities. Do not deploy `-plain.jar` or several FLP versions together.
3. Prefer updating proxy and backends in one maintenance window. If you must upgrade in stages, do not enforce new-format message checks on the proxy before upgrading old backends. Do not enable `require-declared-proxy-policy` on backends until the proxy is upgraded and supports declaring policies.
4. Start and check for configuration, language, proxy trust-list and database warnings. Existing configuration values normally survive; a new default does not automatically replace them all.
5. Use test accounts to check the premium and password login methods your server uses, as well as account switching and reconnecting. Confirm that player data and account state are as expected.
6. Reopen to players only when exact supported combinations are checked. Keep a recovery operator available.

Do not copy the removed `anti-bot.action` or old Fabric `.properties` settings into a new version. Choose your platform in the [configuration reference](../configuration/README.md) when changing login behavior.

## Rollback

Stop writes before reverting. Older plugins can interpret new schema/account states incorrectly, so do not merely swap JARs after account conversion. Restore a compatible, coordinated backup of FLP and auth state and assess player-data changes separately. Commands still queued at rollback time can later reapply old admin intent.

Never regenerate a proxy ID or clear a database as a routine rollback step. If a backup is unavailable or stores disagree, preserve the evidence and reconcile account ownership under operator control rather than allowing all logins.
