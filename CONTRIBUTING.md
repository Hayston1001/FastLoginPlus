# Contributing to FastLoginPlus

[简体中文](CONTRIBUTING_zh.md)

Code, documentation, translations, testing and useful bug reports are welcome.
FastLoginPlus is a maintained fork of [FastLogin](https://github.com/TuxCoding/FastLogin).
Preserve source attribution and compatibility packages. If a fix also applies upstream,
consider a separate minimal upstream pull request.

## Get started

1. Fork the repository, clone your fork and create a branch from the intended base.
2. Install JDK 25 and use it to run the Gradle wrapper. Classic modules use a JDK 21
   toolchain; Fabric uses JDK 25. These build requirements are separate from the
   Java versions required to run the plugin and server.
3. Make your change and run the relevant tests. Before requesting review, normally
   run the full build from the repository root:

   Windows:

   ```powershell
   .\gradlew.bat build --console=plain
   ```

   macOS / Linux:

   ```sh
   ./gradlew build --console=plain
   ```

4. Push your branch and open a pull request against **Hayston1001/FastLoginPlus:main**,
   not the upstream repository. Draft pull requests are welcome.

For a focused check, use a module task such as `./gradlew :velocity:build` (on Windows,
use `.\gradlew.bat`). The full build runs tests and checks across the project, including
Fabric. See the [build guide](docs/en/development/build.md) for more.

## Choose checks for your change

- Test changed decisions and failure paths, not just the happy path. For authentication
  or storage changes, consider disconnects, retries, failed reads and writes, and reloads.
- Changes to shared code need checks for affected platforms. Mirror applicable Bukkit
  changes in Folia while preserving scheduler differences, and verify both.
- Protocol changes need checks for `protocol`, Velocity and Fabric together.
- For storage or JDBC compatibility changes, also run `./gradlew :core:sqliteFloorTest
  --console=plain` (or `.\gradlew.bat :core:sqliteFloorTest --console=plain` on Windows).
- For identity-related changes, run `:craftapi:test` and preserve the UUID golden tests.
  Compilation alone does not prove packaged runtime compatibility.

Report what you actually ran and what remains unverified. Unit tests, a build, server
startup, real-client login and fault injection provide different evidence.

## Project guidelines

- Read the [login and identity trust model](docs/en/LOGIN-FLOW.md) before changing
  authentication. A name lookup, UUID shape or plugin message alone is not proof of identity.
- Keep platform APIs out of shared core logic. The standalone `bungeecord-config` YAML
  library is an exception, not permission to use BungeeCord platform APIs in core.
  See the [module boundaries](docs/en/development/architecture.md).
- Preserve deliberate driver and dependency compatibility baselines. Follow Checkstyle,
  `.gitattributes` and the existing MIT SPDX short headers, retaining original attribution
  where applicable. Keep comments concise.
- Update relevant defaults, platform descriptors and user documentation when changing
  public configuration, commands or behavior. Keep `docs/en/` and `docs/zh/` and their
  navigation aligned. Preserve message placeholders and check both built-in languages
  and fallback behavior. See [languages](docs/en/operations/languages.md).
- Do not commit secrets, test server data or generated local artifacts.

## Pull requests and reports

Use the [PR template](.github/pull_request_template.md) to explain the change, related
issues, checks and remaining gaps. Keep the change focused and check the current CI result.

For bugs, use the [reporting guide](docs/en/operations/reporting.md) and remove secrets
and personal data from logs and configuration. For security vulnerabilities, use GitHub's
private vulnerability reporting if available; do not publish exploit details or secrets
in an issue.

Share feature requests and suggestions in
[Ideas](https://github.com/Hayston1001/FastLoginPlus/discussions/categories/ideas).

Contributions are licensed under the project's [MIT License](LICENSE).
