# Contributing to FastLoginPlus

[简体中文](CONTRIBUTING_zh.md)

Contributions include code, documentation, translations and testing. This is a maintained fork of [FastLogin](https://github.com/TuxCoding/FastLogin). Keep provenance and compatibility packages intact; consider a separate minimal upstream PR where a fix also applies there.

## Build and test

Use the checked-in Gradle wrapper with **JDK 25** to run Gradle. Classic modules use a JDK 21 toolchain; Fabric uses 25. Plugin runtime floors are separate from these build requirements.

```powershell
.\gradlew.bat build --console=plain
.\gradlew.bat :core:sqliteFloorTest --console=plain
```

On Unix-like systems use `./gradlew`. `release` builds every module and collects five installable JARs in `build/release/`. Do not deploy `-plain.jar` or publish/tag just to test a change. Detailed [build checks](docs/en/development/build.md) and [module boundaries](docs/en/development/architecture.md) live in the manual.

## Before editing

- Check the current branch, working tree and affected source. Do not overwrite unrelated work.
- Read the [login/identity trust model](docs/en/LOGIN-FLOW.md) for authentication changes. A name lookup, UUID shape or plugin message alone is not identity proof.
- Keep platform APIs out of shared core logic. Mirror applicable Bukkit changes into Folia while preserving scheduler differences; protocol changes need Velocity and Fabric checks together.
- Preserve the deliberate driver/dependency baselines and UUID golden vectors. Source compilation does not prove packaged runtime compatibility.
- Keep comments concise and licensed source headers in the MIT SPDX short form, with original contributor credit where applicable. Follow Checkstyle and `.gitattributes` (LF, with `.bat`/`.cmd` exceptions).

## Documentation and translations

The full 1.0.0 manual is under `docs/en/`, with matching `docs/zh/` paths. Root READMEs are short entry points, not duplicate configuration manuals. Keep language versions, SUMMARY navigation and public behavior descriptions aligned. Documentation must not cite private/local machine files or imply untested universal compatibility.

Default messages exist separately for classic and Fabric platforms, with shared and platform-specific keys. Preserve replacement tokens and language-version behavior. Check both built-in languages and selected-language/English fallback when changing messages. See [languages](docs/en/operations/languages.md) and [Wiki maintenance](docs/en/development/gitbook.md).

Public config/command/behavior changes also need the relevant defaults, descriptors and user documentation. Release-note changes belong in `CHANGELOG.md` and `CHANGELOG_zh.md` under the appropriate development/release section as authorized; do not mix both languages into one file's new entries or rewrite unrelated historical releases.

## Pull requests

1. Fork the repository and branch from the intended base. Target **Hayston1001/FastLoginPlus:main**, not the upstream repository by accident.
2. Use a concise Conventional Commit-style summary where practical and the [PR template](.github/pull_request_template.md). Reference relevant issues with their repository when needed.
3. Add tests for changed decisions and failure paths, not just the happy path. Run the affected checks and normally the full `build` before requesting review.
4. Describe actual evidence separately: unit tests, compilation, startup, real clients and fault injection. Include disconnect/reconnect, failed reads/writes and shutdown/reload where relevant.
5. Keep secrets, test server data and generated local artifacts out of commits. Stage deliberately, not with an indiscriminate add of the whole working tree.
6. Check current CI results. Draft PRs are welcome for unfinished work. Committing, pushing, tagging and publishing are separate explicit operations, not implied by preparation.

## Reporting and license

Follow the [reporting guide](docs/en/operations/reporting.md), including sanitized versions/config/logs. Use private vulnerability reporting where available for sensitive security issues.

Contributions are licensed under the project's [MIT License](LICENSE).
