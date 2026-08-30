# ProtocolLib async listener — design decision and residual risk

FastLoginPlus intercepts the Minecraft login packets (`START`, `ENCRYPTION_BEGIN`)
with ProtocolLib. This document records **why the listener is registered as an
async handler**, which compensations keep that safe, what risk remains, and when
the decision should be re-evaluated (0.5.0/F003).

## Decision

The packet listener stays registered with `.optionAsync()` +
`getAsynchronousManager().registerAsyncHandler(...)`.

Reasons:

- A full sync-listener conversion (cancel + `runAsync`, removing the async
  marker machinery) requires live integration testing across ProtocolLib
  versions. FastLoginPlus builds against ProtocolLib **5.3.0** (provided
  scope), while production servers may run newer ProtocolLib releases where the
  internal registration semantics differ. The required regression matrix
  (Spigot/Paper × ProtocolLib 5.3–5.x × MC versions) is disproportionate to the
  benefit, given the compensations below already cover the main races.
- The upstream post-mortem that recommends synchronous processing assumed the
  async cancel path races Paper's vanilla handler. FastLoginPlus already
  serializes the sensitive operations on the event loop (see below), which
  removes the *known* manifestation of that race.

## Compensations (in place)

1. **Event-loop serialization of the fake START / enableEncryption window.**
   The response that switches the connection into online-mode verification is
   sent while holding the packet event's processing lock (upstream-style
   `synchronized (packetEvent.getAsyncMarker().getProcessingLock())`), so the
   vanilla state machine cannot observe a half-applied switch.
2. **30-minute async marker timeout.** ProtocolLib's async marker cleanup is
   bounded so a stuck marker cannot wedge a connection forever.
3. **Cancel + signal discipline.** Cancellation of the packet event and the
   session bookkeeping happen in a fixed order; sessions are keyed by the
   connection's remote address (Velocity) or address (Bukkit) and guarded by
   atomic check-and-add (0.5.0/F001).
4. **Startup self-check for ENCRYPTION_BEGIN resolvability.** On registration
   the plugin checks whether ProtocolLib still statically resolves
   `PacketType.Login.Client.ENCRYPTION_BEGIN`. When the mapping is missing
   (the known failure mode on newer ProtocolLib/MC combinations, e.g.
   Paper 1.21.11 + ProtocolLib 5.5.0 where `ServerboundKeyPacket` is not
   registered), a loud startup warning is emitted.

## Residual risk

On ProtocolLib versions where `ENCRYPTION_BEGIN` is **not** intercepted
(mapping missing and the runtime override fallback cannot recover it),
FastLoginPlus' session verification silently never runs: logins proceed as
plain cracked/offline logins. Symptoms: missing `Verifying session for ...`
log lines, AuthMe registration/password prompts for premium players, missing
skin forwarding, no premium kick/ack behaviour.

**Operator guidance** when the startup self-check warning appears:

1. Do not ignore the warning if you rely on premium auto-login; premium
   detection will not work on this server.
2. Pin/upgrade ProtocolLib to a version that resolves
   `ServerboundKeyPacket`/`ENCRYPTION_BEGIN` for your Minecraft version
   (verify with `/protocol log` or a test login looking for the
   `Verifying session` log line).
3. If no compatible ProtocolLib exists for your server version yet, report it
   on the FastLoginPlus issue tracker with your server + ProtocolLib versions
   so the runtime override table can be extended.
4. As a stopgap, run the backend in forced-offline awareness: users keep
   using `/flp premium`-managed cracked sessions, or bypass FLP by
   disconnecting premium verification for that backend.

## Re-evaluation triggers

Revisit this decision (and consider the synchronous registration rework) when
any of the following becomes true:

- upstream ProtocolLib provides a supported **synchronous interception
  guarantee** for the login pipeline (documented API, not internals);
- the startup self-check warning fires **in real environments** at a relevant
  rate (i.e. the residual risk stops being theoretical);
- a new vanilla state-machine race report appears that the current
  compensations cannot cover.
