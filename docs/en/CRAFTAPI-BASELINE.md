# `craftapi/` — vendored CraftAPI (the Mojang API client)

[中文→](../zh/CRAFTAPI-BASELINE.md)

This module is **not FastLoginPlus code**. It is a vendored copy of the upstream
[CraftAPI](https://github.com/games647/CraftAPI) library, which owns every HTTP interaction FLP has with
Mojang: the name→UUID lookup, the session verification (`hasJoined`), the offline-UUID algorithm and the
UUID storage format. FLP itself implements no Mojang request.

The Gradle module is `:craftapi`; it is consumed through a project dependency and is not published separately.
The Java **package names are unchanged** (`com.github.games647.craftapi`), so `core`, `bukkit`, `folia`,
`bungee` and `velocity` need no source changes. The copy is shaded into all four platform jars.

## Why it is vendored

The last upstream release is not reproducible and cannot be upgraded safely:

- The upstream `0.8.1` tag points at `863ecfac` (2024-05-13, Java 11 + `java.net.http.HttpClient`), while the
  artifact FLP actually shipped is a Java 8 / `HttpURLConnection` build made from `6f0ded9f` (2024-05-05).
  There is no tag for what we shipped.
- The upstream `1.0` line is Java 11 bytecode (`v55`). `javac --release 8` compiles against it without a
  warning, so pulling it in would silently raise the bukkit runtime floor from Java 8 to Java 11 and only
  fail on a user's server with `UnsupportedClassVersionError`. It also drops the public
  `setOutgoingAddresses` / `sslFactory` entry point, i.e. the `ip-addresses` feature.
- Two defects in the shipped build are fixed here (see below): `mojang-request-limit` was inert, and the
  proxy fallback requested the endpoint root instead of the player name.

This is the **baseline record** of the vendored module: which upstream tree it came from, what changed
locally, what must not change silently and how to update it. It is a tracked, evergreen document under
`docs/en/` because it describes a module we own rather than an external dependency we track. The plugin jars are
unaffected: only `craftapi/src/main/resources` is packaged, so this file never reaches a user's server.

## Upstream base

| Item | Value |
|---|---|
| Repository | `https://github.com/games647/CraftAPI` |
| Commit | `6f0ded9f` (2024-05-05) — the tree the shipped `craftapi-0.8.1.jar` was built from |
| Java | `release 8`, bytecode `v52` (asserted by Gradle's `checkRuntimeBytecode`) |
| Sources | 28 main files, 10 test files, one binary test resource (`yggdrasil_session_pubkey.der`) |
| Licenses | [Unlicense](../../craftapi/src/main/resources/META-INF/LICENSE-CraftAPI-Unlicense.txt) (library, public domain) and [MIT](../../craftapi/src/main/resources/META-INF/LICENSE-FastUUID-MIT.txt) (FastUUID, © 2018 Jon Chambers). Both are packaged into every platform jar. |

## Local modifications

Behaviour fixes (all covered by offline tests):

1. `MojangResolver` — a `403` from the primary endpoint switches this resolver instance permanently to
   `api.minecraftservices.com` (the known Mojang misconfiguration WEB-7591 / WEB-7666); if the backup
   answers `403` as well, an `IOException` is thrown instead of treating the player as cracked.
2. `MojangResolver` — 429 handling: a rate-limited direct request is retried once through a proxy with the
   *same complete URL*; if the proxy is limited as well (or none is configured) a `RateLimitException`
   is thrown.
3. `MojangResolver.setMaxNameRequests` — clamps into `0..600` (the old `Math.max(600, value)` turned every
   configured value into 600) and rebuilds the sliding-window limiter, so `mojang-request-limit` is applied.
   `0` means "never query Mojang directly, always use a proxy". `MAX_NAME_REQUESTS_LIMIT` exposes the bound.
4. `MojangResolver.getProxyConnection` — sends the complete URL including the player name to the proxy
   (previously the endpoint root was used, so the name was lost) and treats an empty proxy selection like
   `DIRECT` instead of throwing `IndexOutOfBoundsException`.
5. `MojangResolver.findProfile` — response handling is explicit: `200` with a missing `id`/`name` is an
   `IOException` (never "premium"), `204`/`404` mean "not premium", any other status is an `IOException`.
   `hasJoined(..., null)` is defined as "do not send the `ip` parameter", which `ProxyAgnosticMojangResolver`
   in `core` now relies on; a malformed `200` returns `Optional.empty()` instead of throwing an `NPE`.
6. `AbstractResolver` — connections are created as `HttpURLConnection` (so the offline tests can point the
   endpoints at a local `http://` server) and the outgoing-address SSL socket factory is only installed on
   `HttpsURLConnection`; `readJson` turns a malformed JSON body into an `IOException` instead of letting an
   unchecked `JsonSyntaxException` escape.
7. `TickingRateLimiter` — drops *all* expired buckets per acquisition instead of only the oldest one.
8. `MojangResolver` — static initialiser calling `HttpsURLConnection.getDefaultSSLSocketFactory()`
   (JDK-8197807: the first HTTPS request otherwise pays for the SSL context setup).
9. Endpoint fields (`uuidUrl`, `backupUuidUrl`, `useBackupUuidUrl`, `hasJoinedUrlRaw`,
   `hasJoinedUrlProxyCheck`) are package-private so the tests can redirect them; FLP never exposes them as
   configuration.
10. `MojangResolver` — the sticky `403` state no longer doubles as "this request went to the backup
    endpoint": the endpoint of a request in flight is a parameter (`viaBackupEndpoint`) and the flag is
    `volatile`.  Two concurrent lookups that both receive `403` from the primary now both fall back, instead
    of one of them reading the flag the other one just set and throwing
    `Both Mojang APIs returned 403 Forbidden`.  (FLP looks names up on concurrent login threads, so this was
    reachable in production, and upstream 1.0 has the same shape.)
11. `MojangResolver` — every non-`200` response is consumed before the branch returns, recurses or throws
    (`drainQuietly`), `hasJoined` does the same for its `404`/`204` early return, and a connection that never
    produced a response is released with `disconnect()`.  An unread error body makes the client drop the
    socket instead of returning it to the keep-alive pool, so bursts of `429`s or proxy errors used to leave
    connections behind.
12. `NamePredicate` — the name pattern is `^[a-zA-Z0-9_]{2,16}$`; upstream writes `[a-zA-z0-9]`, whose range
    also matches the six ASCII characters between `Z` and `a` (`[`, `\`, `]`, `^`, `_`, `` ` ``).  A login name
    containing one of them (reachable standalone — the packet listener runs before the server's own name
    check) therefore reached Mojang instead of being short-circuited locally, costing up to three rate-limited
    requests and an `IOException` retry loop per connection.  Not an injection: `.`, `/`, `?`, `&`, `%` and
    whitespace stay rejected, so the name cannot leave its path segment.  **The underscore must stay legal**
    (it was only accepted because of that range), so re-vendoring must not restore `[a-zA-Z0-9]`.

Housekeeping: FLP's MIT license header on every file (FastUUID keeps its upstream notice) and Checkstyle
conformance (`OperatorWrap` line breaks, braces, javadoc `@param`). The only API-visible consequence is
`FastUUID` being `final` and `Textures.KEY` becoming `static final` (same value) — both are referenced from
`bukkit`/`folia`, so the change is compile-checked by the reactor build.

## What must not change silently

- `UUIDAdapter.generateOfflineId`, `toMojangId`, `parseId` — the offline UUID is the player's identity and
  the undashed lowercase form is the database format. `UUIDAdapterGoldenTest` pins both against values
  produced by the previously shipped `craftapi-0.8.1.jar`.
- The dependency versions (currently `gson 2.14.0`, `guava 33.7.1-jre` in the version catalog): `bukkit`/`folia` shade and relocate both,
  `bungee`/`velocity` exclude both on purpose (the proxy ships its own copy). Bumping them changes what
  every platform jar contains.

## Updating

Upstream is *responsive but idle*: no release since 2024-05, one branch (`main`), and `main` is the Java 11
line. Before importing anything from upstream: re-check the channel/tag drift, the Java floor and the dropped
`ip-addresses` entry point (all three are described above), re-apply the modification list, then run
`./gradlew :craftapi:test` and the parity/artifact checks below.

## Verification

- `./gradlew :craftapi:test` — 78 tests, no network access (upstream suite plus the offline `HttpServer` / raw-socket
  suites and the golden vectors).
- Class parity with the shipped `craftapi-0.8.1.jar`: the same 32 class names; the only signature changes are
  the intentional ones above plus compiler-generated enum/lambda naming.
- Every platform jar contains exactly one copy of the 32 classes, all `v52`, plus the two license files.
- `./gradlew :core:dependencies --configuration compileClasspath` shows the local `:craftapi` project dependency.
