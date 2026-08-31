# Contributing Guide

Thank you for your interest in contributing to FastLoginPlus! This guide covers
everything you need to build the project, follow its conventions, and submit a
pull request that passes CI on the first try.

FastLoginPlus is an actively maintained fork of
[FastLogin](https://github.com/TuxCoding/FastLogin) — a Minecraft server plugin
that automatically detects and logs in premium (paid) accounts on offline-mode
servers. If your change fixes a bug that also exists upstream, consider whether
it is upstream-worthy; the package name and artifact layout are intentionally
kept compatible.

You can contribute in many ways:

- **Code** — bug fixes, features, platform compatibility (Bukkit, Folia,
  BungeeCord, Velocity)
- **Translations** — add or extend `messages_<lang>.yml` files
- **Documentation** — README, login-flow docs, javadoc
- **Testing** — try release candidates on different server platforms, auth
  plugins, and Java versions, and report results

## Architectural overview

```mermaid
graph TB
    subgraph "Minecraft Server Platforms"
        SPIGOT["Spigot/Paper<br/>(Bukkit Module)"]
        FOLIA["Folia<br/>(Folia Module)<br/>hand-mirrored copy of Bukkit"]
        BUNGEE["BungeeCord<br/>(Bungee Module)"]
        VELOCITY["Velocity<br/>(Velocity Module)"]
    end

    subgraph "FastLogin Core"
        CORE["FastLoginCore<br/>Main Logic Engine"]
        SESSION["LoginSession<br/>Session Management"]
        JOINMGMT["JoinManagement<br/>Login-Flow Template Method"]
        AUTH["AuthPlugin Hook<br/>Auth Integration"]
        RESOLVER["ProxyAgnosticMojangResolver<br/>Profile Resolution"]
        STORAGE["SQLStorage<br/>Database Layer"]
        ANTIBOT["AntiBotService<br/>Anti-Bot Orchestration<br/>(rate limiting, IP bans,<br/>trusted IPs)"]
    end

    subgraph "Bedrock Support"
        FLOODGATE["FloodgateManagement<br/>Bedrock Players"]
        GEYSER["GeyserService<br/>Geyser Integration"]
        BEDROCK["BedrockService<br/>Base Service"]
    end

    subgraph "Bukkit/Folia Compat"
        AUTHME["AuthMe 6.0 Integration<br/>(reflection-based)"]
        PAPI["PremiumPlaceholder<br/>(PlaceholderAPI Expansion)"]
    end

    subgraph "External Services"
        MOJANG["Mojang API<br/>api.mojang.com"]
        SESSION_SERVER["Session Server<br/>sessionserver.mojang.com"]
        DATABASE[(SQL Database<br/>MySQL/SQLite)]
    end

    subgraph "Async Processing"
        SCHEDULER["AbstractAsyncScheduler<br/>Thread Pool Management<br/>(multi-release jar)"]
    end

    subgraph "Messaging"
        MESSAGES["ChannelMessage<br/>Proxy Messages<br/>(i.e. BungeeCord)"]
        RELAY["PendingRelayStore<br/>Durable Relay Queue"]
        NAMEKEY["NamespaceKey<br/>Message Routing"]
    end

    SPIGOT -->|loads| CORE
    FOLIA -->|loads| CORE
    BUNGEE -->|loads| CORE
    VELOCITY -->|loads| CORE

    CORE -->|manages| SESSION
    CORE -->|runs flow| JOINMGMT
    CORE -->|uses| AUTH
    CORE -->|resolves profiles| RESOLVER
    CORE -->|persists data| STORAGE
    CORE -->|checks rate limits| ANTIBOT
    CORE -->|handles bedrock| FLOODGATE

    FLOODGATE -->|extends| BEDROCK
    GEYSER -->|extends| BEDROCK

    RESOLVER -->|queries| MOJANG
    RESOLVER -->|verifies| SESSION_SERVER

    STORAGE -->|connects to| DATABASE

    CORE -->|schedules async| SCHEDULER

    MESSAGES -->|uses| NAMEKEY
    CORE -->|sends via| MESSAGES
    RELAY -->|queues for| MESSAGES

    AUTH -.->|delegates to| SPIGOT
    AUTH -.->|delegates to| FOLIA
    AUTH -.->|delegates to| BUNGEE
    AUTHME -.->|injects into| SPIGOT
    PAPI -.->|registers on| SPIGOT

    ANTIBOT -->|rate limits| RESOLVER
```

A detailed, source-verified description of the login decision flow lives in
[LOGIN-FLOW.md](LOGIN-FLOW.md) — read it before touching `JoinManagement`,
listeners, or the proxy relay path.

## Project layout

| Module    | Java release | Description                                                        |
|-----------|--------------|--------------------------------------------------------------------|
| `core`    | 8            | Shared library: login flow, storage, anti-bot, messaging, events   |
| `bukkit`  | 8            | Spigot/Paper plugin (ProtocolLib packet handling, auth-plugin hooks) |
| `folia`   | 21           | Folia plugin — **manual, hand-maintained copy of `bukkit`** adapted to regionized scheduling |
| `bungee`  | 17           | BungeeCord proxy plugin                                            |
| `velocity`| 17           | Velocity proxy plugin                                              |

Build requirements:

- **JDK 21** (the version pinned in `.java-version` and used by CI). The
  per-module `maven.compiler.release` settings above make javac reject APIs
  newer than each module's target, so a single modern JDK is all you need —
  but do not use Java-9+ APIs in `core`/`bukkit` or Java-18+ APIs in
  `bungee`/`velocity`.
- **Maven 3.9+**. A git clone is expected (the build embeds the commit hash
- **Maven 3.6.3+** (required by the plugins in use; 3.9.x is used for development).
  A git clone is expected — the build embeds the commit hash into the final
  JAR name and manifest.

Some auth-plugin APIs (CrazyLogin, UltraAuth, BungeeAuth) are provided as
system-scoped JARs in the `lib/` directory of each module — no manual
installation is required.

## Building

```bash
# Build all modules, skipping tests
mvn package --batch-mode -DskipTests

# Build and run the test suite (what CI does)
mvn package --batch-mode

# Run tests only
mvn test --batch-mode

# Build a single module (with its dependencies, here: core)
mvn package -pl bukkit -am --batch-mode -DskipTests
mvn package -pl folia -am --batch-mode -DskipTests
```

Finished JARs land in the respective `target/` directories, named
Finished JARs land in each module's `target/` directory, named like
`FastLoginPlusBukkit-<version>-<commit>` (module name + revision + commit hash).

## Enforced checks — the build fails without these

These run on **every** build (locally and in CI). Save yourself a round-trip
and verify before pushing:

1. **MIT license header** — every Java and XML file must carry the project
   license header (`license-maven-plugin`, checked against the root `LICENSE`
   file; resources and `.java-version` are excluded). When creating a new
   file, copy the header from an existing one.
2. **Checkstyle** (`checkstyle.xml`, severity `error`, `failsOnError`).
   Highlights beyond the usual naming/whitespace rules:
   - Line length ≤ **120** characters (Java files)
   - Methods ≤ **160** lines; `final` parameters; no star imports; no unused
     imports; no tabs
   - `MagicNumber` is on — extract literals into named constants
   - `MissingSwitchDefault` — every `switch` needs a `default` branch
   - `DesignForExtension`, `FinalClass`, `HideUtilityClassConstructor` —
     design-for-inheritance rules; mark utility classes `final` with private
     constructors, and make classes `final` unless extension is intended
   - Javadoc: `@param`/`@return`/`@throws` required on documented methods;
     package-level javadoc (`JavadocPackage`) is checked
3. **Line endings and final newline** — `.gitattributes` normalizes all text
   files to LF and every file must end with a newline (`NewlineAtEndOfFile`).
   On Windows, let git handle conversion; do not commit CRLF.

## Testing

- Tests use **JUnit 5** and **Mockito (inline mock maker — required for static
  mocks)**; both are declared in the root POM.
- Unit tests live in each module's `src/test/java`; `bukkit` additionally has
  an `integration` test package.
- Add tests for bug fixes (a failing-test-first commit for non-trivial bugs is
  appreciated) and for new decision logic in `core`.
- If you touch packet handling or login flow, at minimum add/extend tests
  around the affected `core` logic — full cross-platform behavior needs manual
  testing, which you should describe in your PR (platforms, server versions,
  auth plugins tried).

## Platform-specific conventions

- **Folia mirrors Bukkit by hand.** `folia/src/main/java` contains a manual
  copy of the `bukkit` sources (same package `com.github.games647.fastlogin.bukkit`)
  with regionized-scheduler adaptations (`FoliaScheduler`). If your change
  applies to both platforms, port it to `folia/` yourself — CI will not remind
  you. Changes limited to scheduling-sensitive code should account for the
  two schedulers' different APIs.
- **Permissions** follow `fastloginplus.bukkit.command.*` (bukkit) and
  `fastloginplus.folia.command.*` (folia); they are resolved at build time
  from `${project.artifactId}` in `plugin.yml`.
- **Language files** — user-facing messages live in
  `core/src/main/resources/messages_en.yml` and `messages_zh.yml`. New keys
  must be added to both; English is the fallback that auto-fills missing keys.
  New translations are welcome: add `messages_<lang>.yml` with the same keys.
- **Config templates** — `config.yml` (backend servers) and `config-proxy.yml`
  (BungeeCord/Velocity, trimmed of backend-only keys) both exist on purpose.
  When adding a config option, decide which template(s) it belongs in and
  update both files as needed. Defaults shown to users come from these
  templates, not from code.
- **Shaded dependencies** — HikariCP, SLF4J, SnakeYAML, and Gson are relocated
  into the final JARs (relocation sets differ per module; see the shade-plugin
  configs). `sqlite-jdbc`/`mariadb` are `provided` in `core`/`bukkit` (the
  server ships them) but bundled in `bungee`/`velocity`. Keep this in mind
  when adding dependencies — prefer `provided` scope for anything a modern
  server already provides.

## Commit messages

The project follows **Conventional Commits** style:

```
<type>(<optional scope>): <short summary in lowercase>
```

Types seen in history: `feat`, `fix`, `docs`, `test`, `chore`, `version`.
Useful scopes: module names (`bukkit`, `folia`, `bungee`, `velocity`, `core`),
or areas (`storage`, `proxy-msg`, `config`, `changelog`).

## Pull requests

1. Fork the repository and create a feature branch off `main`.
2. Run `mvn package --batch-mode` locally — all checks above must pass.
3. Open the PR against `main` using the provided
   [PR template](.github/pull_request_template.md): a clear summary of the
   change and a reference to the related issue (`Fixes #123`).
4. If the work is still in progress, open a **draft PR** rather than waiting.
5. CI builds every push/PR to `main` and runs a CodeQL security scan on the
   result — green CI is required before merge.
6. For user-visible changes, add an entry to [CHANGELOG.md](CHANGELOG.md)
   under the current development version.
7. If your change affects user-facing setup or behavior described in the
   README, update both [README.md](README.md) and
   [README_zh.md](README_zh.md) — the two are kept in sync.

## Reporting bugs

When opening an issue, include:

- FastLoginPlus version (and where you got it from)
- Server platform and version (Paper/Spigot/Folia/BungeeCord/Velocity)
- Auth plugin (name + version), and whether a proxy is involved
- Relevant log excerpts (enable debug output if asked — `debug: true` in the
  config) and your `config.yml` with secrets removed
- For login issues: whether the account is premium, and what the player sees

## Additional developer documentation

- [LOGIN-FLOW.md](LOGIN-FLOW.md) — the full login decision tree, verified
  against the source
- [PROTOCOLLIB-ASYNC-DESIGN.md](PROTOCOLLIB-ASYNC-DESIGN.md) — why the packet
  listener is async and which races are compensated; read before changing
  ProtocolLib listener code

## License

By contributing, you agree that your contributions will be licensed under the
project's [MIT License](LICENSE).
