# Infernal crash audit — 2026-10-08

## Diagnosis from the full log

The first pasted excerpt ended during mod discovery and omitted the exception. The subsequently supplied `C:\Users\LENOVO\Downloads\latest.log` contains the fatal exception at lines 20946–20951, logged on 2026-09-26 at 23:44:50:

```text
java.util.ConcurrentModificationException
  java.util.ArrayList$Itr.checkForComodification
  java.util.ArrayList$Itr.next
  InfernalChargeVfxRenderer.tickParticles(InfernalChargeVfxRenderer.java:308)
  InfernalChargeVfxRenderer.render(InfernalChargeVfxRenderer.java:180)
  BeamRenderer.onRenderLevel(BeamRenderer.java:407)
```

This confirms the fatal crash occurs while JeeCore renders Infernal ignition particles: its particle `ArrayList` changes during iteration. The reviewed code's rage melee proc called `InfernalChargeVfxRenderer.detonate` directly from the server thread, which appends to that same list while the client renders it. That provides a concrete producer path explaining the observed exception. The previously implemented packet-dispatch fix removes this path and confines these VFX additions to queued client work. All remaining external calls to `prime`/`detonate` in the workspace are in `IgnitionChargeVfxPayload`'s queued handler.

The failing installed artifact is `jeecore-dev-1.1.0.jar` (internal version 1.0.0). Its stack line numbers differ from the current workspace. This is a diagnosis supported by the log and source, not an in-game reproduction of the installed modpack. The full log records a successful world join and gameplay; the earlier duplicate-Citadel suspicion does not explain this fatal stack.

## Findings and fixes

| Area | Trigger and failure mechanism | Change |
| --- | --- | --- |
| Rage melee VFX | A raging player's attack passes the 45% proc roll. The common server event handler invokes a client renderer. Dedicated servers can fail loading client classes. In single-player the server and render threads access the renderer's `RandomSource` and particle `ArrayList` concurrently, risking random-source thread exceptions or concurrent modification. | Replaced the renderer call with the existing `IgnitionChargeVfxPayload`, sent to tracking clients and the target player when applicable. VFX creation runs through the client's queued packet handler. Added server-side guards to attack and damage processing. |
| Rage state reads | Rage ends between `containsKey` and a subsequent `get`, or between two `get` calls. Unboxing a now-missing expiry throws `NullPointerException`. The static map is shared by logical client and server in single-player. | Read the expiry once into a nullable local. Expiry cleanup conditionally removes the observed value. Login sync uses the iteration entry's expiry rather than fetching it again. |
| Smoke-exit render pass | `BeamRenderer` calls smoke-exit rendering while its shared mesh is still being built. Smoke-exit rendering starts and uploads other meshes from the same tessellator backing allocator, and changes blending before the shared mesh is uploaded. | Finish and upload the shared mesh first, then run smoke-exit's separate passes. Removed its unused shared-buffer argument. This fixes an unsafe allocation/render ordering; no GPU reproduction was performed. |
| VFX without a client world | A queued smoke-exit or molten-ground effect reaches its renderer while `Minecraft.level` is null, such as during disconnect or a world transition. Both entry points immediately dereferenced the level. | Both entry points now return when no client world exists. |
| Delayed ignition explosions | A global numeric-entity-ID map is scanned once per Infernal player. A player in another dimension can delete pending entries because their level cannot find the target. Entries can stop progressing after the attacker leaves or switches gene. Detonation occurred before removing the entry, allowing callback reentrancy; packet dispatch also repeated an entity lookup without checking for null. | Scoped queues to `ServerLevel` and entity UUID. Process once per level post-tick, independently of Infernal players. Iterate a snapshot and remove the due entry before invoking explosion callbacks. Dispatch using the resolved target and include target players. Clear queues at server shutdown. Missing/unloaded targets cancel their pending entry. |

## Reviewed scope and remaining gaps

Reviewed Infernal passive attributes and water damage, smoke movement/effects, ignition charging/QTE/timeout/delayed explosions, rage state/attributes, pounce movement/impact, Infernal packet handlers, particle rendering, and their shared event/render entry points.

Additional lifecycle and performance gaps remain, without evidence that they caused this crash:

- Rage and pounce state lacks a complete logout/gene-switch cleanup path. Shared static client/server rage state also permits state interference in single-player. A broader lifecycle change should separate logical sides and consistently clear/resync state on logout, death, dimension changes, and gene changes.
- Some impact and exit particles age during rendering, so their lifetime/workload depends on frame rate. Some VFX collections also retain state across world changes. These warrant tick-based aging, world lifecycle cleanup, and particle caps if profiling identifies a problem.
- The startup excerpt lists both `citadel-1.21.1-2.7.6.jar` and `citadel-2.7.1-1.21.1.jar`. Check the full loader output for duplicate-mod errors and keep the pack's intended single Citadel version. The excerpt alone does not establish that this caused the crash.

## Validation

- `gradlew.bat build --offline --no-daemon` passed using Java 21.
- Added `infernalRageRegressionTest` to `check`, using a standalone regression source set and existing Minecraft compile dependencies. No new test-library downloads are needed.
- The regression checks missing/active/ended rage state and stresses concurrent activation/removal with one writer and three readers, 200,000 iterations each. Passed.
- No interactive client or dedicated server reproduction was performed. The build uses the workspace's NeoForge 21.1.228; the supplied log uses 21.1.248. Full-modpack behavior remains unverified.
- Built artifact: `build/libs/jeecore-1.0.0.jar`, reflecting the workspace's existing version metadata. Replace the old JeeCore JAR when testing; do not keep both installed.

## In-game verification

1. Attack mobs repeatedly while raging, in single-player and on a dedicated server. Confirm explosion VFX reaches observers and no thread/classloading exceptions appear.
2. Exit smoke form while other ability effects are visible, in first and third person. Check smoke and beam rendering.
3. Use a successful ignition QTE against a mob and a player; test landing, death, unloading, dimension changes, and the attacker switching gene or leaving. Pending effects in one dimension should survive activity in another; unloaded targets should cancel safely.
4. Trigger smoke exit and aerial impact around disconnect/respawn transitions. Confirm no world-null VFX exception.
5. Retest with the rebuilt JAR replacing `jeecore-dev-1.1.0.jar`; preserve a new log if any crash remains. The provided full log already confirms the particle-iteration crash described above.
