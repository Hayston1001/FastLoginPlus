# Database configuration

## Which database?

Standalone FLP stores its premium/cracked records on that server. Proxy deployments use the **proxy's** FLP database; classic backends do not open a second FLP list database in proxy mode. The auth plugin's database remains a separate local account store, including EasyAuth on Fabric.

SQLite is the default. Do not put one SQLite file on a network share for several processes or copy a live database without a consistent backup. Multi-proxy or multi-backend account synchronization needs its own design and testing; choosing MySQL does not solve every cross-server race or local-account mismatch.

## Configure MySQL or MariaDB

If you do not need an external database, keep the default SQLite configuration. To use MySQL or MariaDB:

1. Create a database and dedicated account on the database server, granting it the permissions required for that database.
2. Stop the server running FLP. On a standalone server, edit its `plugins/fastloginplus/config.yml`; in a proxy deployment, edit that path on the proxy.
3. Change the existing options below, replacing the examples with your actual details:

   ```yaml
   driver: 'mysql'
   database: 'fastloginplus'
   host: '127.0.0.1'
   port: 3306
   username: 'flp_user'
   password: 'replace-with-database-password'
   ```

4. Configure encrypted connections as required by your database and driver. BungeeCord does not bundle the MariaDB driver; confirm that a compatible driver is available before switching.
5. Start the server and check for database connection errors. Existing SQLite data is not migrated automatically when you change the configuration; back it up and plan the migration separately before switching an existing server.

These settings configure FLP's list database, not the authentication plugin's account database.

## Options

| Option | Default | Meaning |
| --- | --- | --- |
| `driver` | `sqlite` | FLP store; use `mysql` for the MySQL/MariaDB configuration |
| `database` | `{pluginDir}/FastLogin.db` | SQLite path; for MySQL, database name instead |
| `host` | `127.0.0.1` | MySQL host |
| `port` | `3306` | MySQL port |
| `username` | `myUser` | Replace with a dedicated database user |
| `password` | `myPassword` | Replace; do not publish it |
| `timeout` | `30` | Connection-pool acquisition timeout, seconds |
| `lifetime` | `1800` | Pool connection maximum lifetime, seconds; 0 means unlimited; positive values below 300 are clamped |
| `useSSL` | `false` | Enable the configured MySQL SSL path |
| `sslMode` | `Required` | Passed to the JDBC driver on that SSL path; review driver verification modes |
| `allowPublicKeyRetrieval` | `false` | MySQL authentication key-retrieval option on the SSL path |
| `ServerRSAPublicKeyFile` | Unset | Local RSA public-key file alternative, if required by the chosen driver/authentication |

These FLP options do not configure EasyAuth's storage. Fabric's supported integration uses EasyAuth's local SQLite and separately reads its account state. Do not add `driver` to Fabric's backend-local YAML.

## MySQL/MariaDB setup precautions

Create the database and a least-privileged account before starting FLP. Keep `lifetime` below the server's idle timeout. Review TLS and authentication options against the exact JDBC driver; `useSSL` alone is not a claim of hostname/certificate validation.

Runtime drivers differ by artifact: Bukkit/Folia normally use server-provided JDBC drivers; Velocity bundles SQLite and MariaDB, while BungeeCord bundles SQLite but does not bundle the MariaDB driver. Do not assume the same MySQL configuration is self-contained on both proxies; check driver availability before switching. The existing SQL support does not justify dropping an arbitrary modern driver onto a Java-8 server.

## Backup and errors

Back up FLP records, auth accounts, pending queues, proxy IDs and player data together. A FLP-only backup cannot restore passwords or undo world-data changes. On read/write failures, inspect the error and persistent state before retrying account commands. Database failures must not be "fixed" by treating every name as new.

See [backup and upgrades](../operations/upgrading.md) and [list semantics](../accounts/lists.md).
