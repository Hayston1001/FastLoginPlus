# Build, test and contribute

## Build environment

Clone the repository with Git and run the checked-in Gradle wrapper from its root. **Gradle must run on JDK 25**. Classic modules use a JDK 21 compiler toolchain with per-module `--release`; Fabric uses JDK 25. Foojay can provision the classic toolchain, not the JVM needed to start Gradle. Maven instructions from older releases do not apply.

```powershell
.\gradlew.bat build --console=plain
.\gradlew.bat :core:sqliteFloorTest --console=plain
.\gradlew.bat release --console=plain
```

On Unix-like systems use `./gradlew`. Other useful tasks are `test`, `assemble` and scoped builds such as `:bukkit:build :folia:build`. `assemble` is not a substitute for tests/checks. Release preparation should use a clean build and inspect actual artifacts, not reuse old output blindly.

## Outputs and version

The root `build.gradle` is the version authority. Resource processing expands platform descriptors and generates Velocity build info. Classic installable artifacts are the shaded JARs, not `-plain.jar`. `release` builds all modules and collects **five** installable JARs in `build/release/`: Bukkit, Folia, BungeeCord, Velocity and Fabric 26.2. It checks a CI tag/version mismatch and the artifact count.

The manual's forthcoming 1.0.0 target does not mean the current build file was already bumped. Do not change the version, tag, push or publish as a side effect of documenting a feature.

## Checks and test selection

- `build` runs tests, Checkstyle, license and relevant artifact checks. Respect the 120-character Java line limit, 160-line methods, import rules and required Javadoc tags in `checkstyle.xml`.
- Existing licensed sources use MIT SPDX short headers, preserving upstream contributor credit. New Java/XML/Gradle files use the short header; do not paste the full license into each file.
- `checkRuntimeBytecode`, classic `verifyPluginJar` and Fabric `verifyFabricJar` check packaged dependencies/identities. Module source compatibility alone does not establish the runtime floor.
- `:core:sqliteFloorTest` reruns storage tests against the deliberately pinned oldest supported SQLite driver, not just the newest compile dependency. Keep the golden driver-floor assertion when changing storage.
- Identity changes need `:craftapi:test`, especially `UUIDAdapterGoldenTest`, when they touch lookup/session/offline UUID semantics. Protocol changes need protocol, Velocity and Fabric tests together.
- `shared` rejects platform/third-party production imports, resources and package-sensitive reflection. Fabric compiles relocated copies of `protocol` and `shared`; test the generated artifact as well.
- Bungee source must retain the public-API boundary and `BungeePublicApiOnlyTest`.
- Tests use JUnit 6 and Mockito. Passing mocked tests is not proof of server startup, real client authentication or failure recovery.

## Contribution rules

Keep platform APIs out of `core`. Mirror applicable Bukkit changes into Folia by intent, not by copying a directory; preserve region scheduling and different permissions. Shared dependency versions belong in `gradle/libs.versions.toml`, but intentionally pinned server/proxy/SQLite baselines remain in their `gradle/*.gradle` files.

Maintain English and Chinese documentation together. The root READMEs are entry points; detailed behavior belongs in this manual. Update default configs/messages/descriptors when public behavior changes. Record release notes only as part of an authorized change/release workflow, in the correct English/Chinese changelogs.

Open PRs against [Hayston1001/FastLoginPlus](https://github.com/Hayston1001/FastLoginPlus), unless explicitly contributing upstream. State changed behavior and which platforms/tests were actually checked. Keep secrets, runtime server data and local reports out of commits. See the [contribution guide](https://github.com/Hayston1001/FastLoginPlus/blob/main/CONTRIBUTING.md).
