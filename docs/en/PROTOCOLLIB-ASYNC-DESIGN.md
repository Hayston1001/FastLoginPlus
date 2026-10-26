# ProtocolLib async listener design

This reference concerns the **standalone Bukkit/Folia ProtocolLib path**, not proxy-backend identity handling. The current implementation registers `.optionAsync()` with `registerAsyncHandler(...).start()`. Do not describe a historical synchronous-listener experiment as the shipped design.

## Why keep the asynchronous path?

Login processing can involve database/HTTP work. A synchronous rewrite must prove packet cancellation, injected START behavior and vanilla state transitions across actual ProtocolLib/server versions. An old failure report or a single successful experiment is not enough evidence to replace the current path.

The implementation serializes sensitive encryption/profile/fake-START operations on the connection event loop and coordinates ProtocolLib processing locks and marker signalling. Duplicate encryption responses are guarded. Async marker timeouts bound cleanup; they are not a promise that users should wait that long for login.

## Verification must remain real

A Mojang name lookup or a `spoofedUUID` assignment is not a premium session. Never mark a connection verified merely because a UUID was found or injected. Each login has a fresh handshake challenge; cached name/UUID facts cannot replace `hasJoined` or the proxy's verified native handshake.

An injected packet can re-enter version-specific interception layers. Before changing it, trace the deployed ProtocolLib implementation, event cancellation, marker lifecycle and vanilla handler. Do not infer all versions' behavior from the `filters=false` argument alone.

## Mapping failures and diagnostics

Startup checks whether `ENCRYPTION_BEGIN` can be resolved. A runtime override can recover some packet mappings, but not every missing interception. If verification logs are missing, the path may not have run at all; a timeout, rejection or password flow is a symptom, not proof of a safe fallback.

1. Record exact server, ProtocolLib and FLP builds. Enable FLP debug and reproduce with a real client.
2. Correlate START, session creation, encryption response, session-service verdict, event-loop work and account action for the same connection.
3. Verify a packet-library build compatible with the server. Do not assume any higher ProtocolLib version fixes all mappings.
4. Inspect both Bukkit and Folia copies before modifying shared behavior; preserve the scheduler differences.

If no compatible combination is available, keep account protection in place and report the failure. Do not recommend a UUID-only bypass, skipping session verification, or treating `/flp premium` as a way to authenticate an offline session.

## Re-evaluate only with evidence

A supported upstream interception change, reproducible state-machine race or recurring mapping failure can justify a new design. Require regression tests plus live coverage for first login, reconnect, invalid session, service outage, duplicate response and disconnect. Compilation and startup alone do not establish login correctness.

Source: [ProtocolLib listeners/tasks](https://github.com/Hayston1001/FastLoginPlus/tree/main/bukkit/src/main/java/com/github/games647/fastlogin/bukkit/listener/protocollib). See [login trust](LOGIN-FLOW.md) and [troubleshooting](operations/troubleshooting.md).
