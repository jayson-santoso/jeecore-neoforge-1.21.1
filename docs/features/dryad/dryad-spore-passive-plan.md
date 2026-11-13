# Dryad passive: Dryad Spore

Implemented for Minecraft 1.21.1 / NeoForge 21.1.228. This specification reflects the current combat-based behavior.

## Production

A Survival/Adventure Dryad silently enters combat for 600 ticks (30 seconds) after taking damage or dealing damage to a living entity. Every qualifying hit refreshes that window. Actual health damage and damage absorbed by golden hearts count; cancelled, blocked, and zero-damage hits do not. Environmental damage also activates the window. Indirect attacks use the damage source's owning player. No combat icon, message, or HUD timer is shown.

While in combat, make one 40% release roll every 160 ticks (8 seconds). Entering a new combat window starts a full interval. Refreshing an existing window does not postpone the next roll. A failed roll consumes the interval; there are no rapid retries. Out of combat, production stops and the countdown resets. Burning pauses production and prevents releases; combat still expires with server time.

When damage leaves a surviving Dryad strictly below 40% maximum health, independently roll a 30% emergency release chance for one spore. There is no double-spawn roll. Fully absorbed damage also qualifies when the Dryad is already below 40%. Fatal damage does not release spores. Emergency releases do not reset the periodic timer.

The existing maximum of two live natural spores per Dryad applies to both periodic and emergency releases. Placement failure never forces a spawn into terrain. The existing spore-release indicator and audio remain; combat itself stays silent.

## Rewards and collection

- A fresh pickup grants **4 health points / 2 absorption hearts** for up to 240 ticks / 12 seconds.
- Each successful pickup restores **2 hunger points and 4 saturation points**, capped by vanilla food/saturation limits. Obsolete fractional reward credit is discarded.
- A pickup may refresh a partially spent shield to 12 seconds without refilling its absorption.
- When absorption reaches zero, remove Dryad's Blessing and its capacity immediately after damage. A normal effect tick also handles depletion by commands or other mods.
- Depletion, expiry, milk, and death end the ring, column, and green screen overlay with the brief exit animation.
- A new shield can be obtained after depletion, subject to the existing five-second shared pickup cooldown.
- Existing unrelated absorption prevents collection. Removing Dryad's Blessing must preserve unrelated absorption effects and their ordinary capacity/clamping behavior. Minecraft uses a shared absorption pool.

Collectors must be alive, connected, and in Survival/Adventure. The owner can collect its own spore; other players must share the owner's team, or the owner must have no team. Mobs, pets, spectators, Creative players, hoppers, and item collectors cannot consume it.

Pods must land before collection, be at least ten ticks old, and wait thirty ticks before owner collection. Collection uses a one-block bounding-box distance plus line-of-sight checks. The closest eligible player wins, with entity ID as a stable tie breaker. The consumed entity is removed in the same server operation.

The entity is named **Dryad Spore**, and its status effect is **Dryad's Blessing**. Internal registry IDs remain unchanged. The gene-selection UI has no spore tooltip.

## Entity and physics

Each nonsaving miscellaneous entity lives for 200 ticks / 10 seconds from launch, including flight. It uses a 0.45-block collision box and the enlarged cuboid/fin model. Tiny pale-green sparkles and a very subtle short column highlight it; no native Glowing outline is applied.

Launch uses random horizontal direction, 0.11–0.15 blocks/tick horizontally and 0.28–0.34 upward, plus owner momentum. Gravity is 0.04 blocks/tick squared and air drag is 0.99. Vanilla swept collision handles walls, ceilings, slabs, stairs, and touchdown. Removed support resumes falling. Landing effects occur once per actual landing.

Reject unsafe or obstructed launch space, unloaded swept terrain, hazardous landings, fluid, and enclosing blocks. Never force-load chunks. Loaded natural spores disappear if their owner dies, disconnects, changes gene/dimension, or moves more than 24 blocks away. Unload/restart does not preserve them.

During the last forty ticks, alternate model, shadow, and sparkle visibility every two ticks, alongside the shrinking animation. Server-synchronized age keeps late viewers on the same blink phase.

## Visuals and sound

### Release and spore highlight

Launch emits three totem particles at speed 0.20 and two clouds at speed 0.08. Play shulker shoot at pitch 1.8 and volume 0.45. A small ground ring expands and fades over fourteen ticks. The spore emits a tiny fast-moving sparkle every four client ticks, lasting ten ticks (at most three per spore). Camera-facing motes use pale-green halos, white cores, thin crossed rays, and rapid twinkling. Motion and emission advance on client ticks, independent of frame rate. They clear on spore removal, blinking, world changes, and disconnects, and render only within 32 blocks. A 0.45-block-tall pale-green ground column uses only about 8/255 alpha at its base, fading to transparent at the top. No Minecraft Glowing outline is used.

Root-snared entities share the small pale-green/white sparkle shape. Their eight staggered motes move radially outward at 0.045–0.080 blocks/tick and upward at 0.035–0.070, with slight upward acceleration, a 10–14-tick lifetime, rapid twinkling, and new angles on successive cycles. Timing derives from world age rather than frame rate.

After landing, each visible spore also emits one small pale-green vanilla dust particle every twelve client ticks, from a 0.20–0.32-block radius around its base. Dust scale is 0.45 with a gentle drift; no dust is emitted during flight or a hidden expiry-blink phase.

### Heal-ring world lighting

The active elixir heal ring places one invisible, replaceable, noncolliding light source at its center (or up to two blocks above if occupied), emitting torch-level block light (14). This illuminates nearby terrain through Minecraft's ordinary light engine without requiring shaders. Only air is replaced. Overlapping rings share a reference-counted source; the last ring restores the original air state. Player-placed replacement blocks are preserved. Expiry and server shutdown remove owned lights. Persisted scheduled ticks self-remove orphaned lights on reload, including sources unloaded when their ring expires; cleanup never forces chunks to load. A completely occupied center column safely skips light placement.

Landing emits two clouds at speed 0.06 and three block fragments matching the actual support. Play azalea-leaves fall at pitch 0.8 and volume 0.5 for a leafy thud.

### Consumption and protection

Consumption emits exactly five fast totem particles at speed 1.0 from the recipient. Play generic eat at pitch 1.8 / volume 0.45, together with `minecraft:entity.allay.item_taken` at pitch 1.7 / volume 0.55. Server-sent feedback avoids duplicate sounds.

The supplied `textures/effects/spore_ring.png` surrounds the existing `dryad_heal_ring_0.png`, tinted white-dominated pale green. The inner glow, outer ring, and halo pulse at distinct frequencies. Stable UUID offsets give different recipients different phases. The ring follows the recipient's feet and uses collision surfaces to locate the ground.

The recipient's green cylinder pulses between about 0.70 and 1.00 blocks in height. The ring and column remain visible while jumping or falling. They project onto nearby ground within six blocks; if there is no nearby ground, they follow the recipient's feet. Entry takes six ticks; exit takes eight ticks, with expansion/fade and column contraction.

The local screen overlay reuses the existing heal-ring Minecraft nausea texture/rendering path with a green tint. It does not apply the Confusion effect or camera wobble. It fades out when protection ends, including early shield depletion.

## Implementation

Java paths below are under `src/main/java/net/sonflowers/jeecore/`:

| File | Responsibility |
| --- | --- |
| `gene/dryad/server/DryadSporeManager.java` | Silent combat windows, release rolls, caps, placement, rewards, damage/depletion handling, cleanup |
| `gene/dryad/entity/DryadSporeEntity.java` | Ballistic motion, synchronized age/landing, collection, expiry and owner validation |
| `gene/dryad/effect/SporeGuardEffect.java` | Two-heart absorption capacity, duration-only refresh, depleted-pool removal |
| `gene/dryad/server/SporeVisualManager.java` | Start, refresh, end and late-tracking visual synchronization |
| `gene/dryad/network/SporeVfxPayload.java` | Server-authoritative visual state payload |
| `gene/dryad/client/SporeRingRenderer.java` | Ground rings, independent pulses, green columns, entry/exit animation |
| `gene/dryad/client/DryadSporeRenderer.java` | Pod model, bob, rotation, shrink and blink |
| `core/client/BeamRenderer.java` | Shared heal/spore screen overlay path |

Use vanilla entity tracking for position/removal and dedicated payloads only on visual state changes; do not broadcast animation frames every tick. New viewers receive an active protection snapshot. Client decoration cannot grant rewards. Clear server/client caches on their respective lifecycle changes.

## Operator commands

Permission level 2 is required:

- `/jeecore choose`
- `/jeecore rage fill [target]`
- `/jeecore spore spawn`: force one collision-safe ballistic launch, including in Creative or without Dryad, bypassing combat/chance/cap/fire restrictions. Ordinary lifetime and collection rules remain.
- `/jeecore spore clear`: remove all loaded spores across dimensions and reconcile passive caps, including newly added entities not yet publicly tracked.

A weak registry covers newly added spores without retaining discarded entities. Legacy test commands remain removed.

## Validation

Development-only GameTests live in `src/gameTest`, excluded from the main jar. Run `./scripts/run-gametests.ps1`; the isolated world/logs/config live in ignored `runs/gametest/`. Gradle `build` also retains the Infernal regression checks.

Coverage includes combat gating and eight-second/40% rolls, full-interval retries, combat refresh from living-target damage, thirty-second combat expiry, low-health boundaries, emergency single-spore chance boundaries and caps, two-heart grant/refresh/depletion, immediate visual exit on depletion, consistent two-point hunger/saturation rewards, high-pitched eat/allay item-taken audio packets, teams/cooldowns/arbitration/obstruction, flight/collision/landing/hazards, lifetime/blinking, commands/global clear, tracking/milk/refresh synchronization and payload serialization. The laser test checks four cosy campfire smoke particles with 0.3 spread per axis and 0.2 speed at the block-impact face. Sustained lasers emit the puff every eight ticks; burst block hits emit one puff, with a 0.06-block face offset. Misses and entity-stopped burst hits do not emit block smoke. Lighting tests verify real terrain illumination/removal, shared leases, original-air restoration, replacement-block protection, obstructed placement, orphan cleanup, and actual elixir-ring expiry.

Gradle build, the existing Infernal regression checks, and all 41 required headless GameTests passed for the latest revision, including six Terragolem charge/recoil cases; see `terragolem-beam-movement.md`.

Headless checks cannot validate the rendered appearance or subjective audio balance. Manual checks remain: daylight/caves, jumping and landing, two graphical clients, late tracking, shader/particle settings, and combat balance.
