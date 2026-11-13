# Terragolem impact concussion

A server-side passive combines Minecraft's LivingFallEvent with a per-player airborne height trace. The trace includes the final landing step and still detects impacts when beam/ready-up code resets vanilla fall distance. It resets across liquid, flight, riding, gene/entity/dimension changes, large position jumps, and logout. Falls below six blocks do nothing. Radius is `4 + clamp((fallDistance - 6) / 6, 0, 1) * 2`: six blocks of fall gives a four-block radius, nine gives five, and twelve or more gives six. Existing fall damage, armor reduction, ability cooldowns, and laser behavior are unchanged.

The caster must actually contact a solid, dry support near their feet. Liquid/waterlogged supports, swimming, lava, riding, elytra flight, active creative flight, spectators, other genes, and invalid fall distances are excluded. Duplicate reports of the same landing in one tick emit only one wave.

## Ground ripple

The server samples one exposed colliding ground block per column, with a maximum horizontal radius of six. A flood fill follows connected dry ground and small steps within one block of the impact height. Water gaps, unsupported columns, tall walls, unloaded chunks, and unsupported elevated floors do not form a path. Only block-model surfaces are copied; block entities are skipped.

The front travels half a block per tick, reaching the six-block edge in twelve ticks. When it reaches a sampled column, a real vanilla BlockDisplay copies that position's complete block state, including orientation and shape. Its transformation rises 0.28 blocks over two ticks, then returns over three ticks. Every transform carries an explicit block/sky brightness override sampled above the supporting block, with a minimum block-light value of four for readability. This avoids sampling darkness from the display origin inside the opaque ground. A vanilla dust-pillar particle matching the complete block state appears at each column as the wave reaches it. A short spawn delay lets clients receive the display before its first interpolation. Each display discards itself after ten ticks, or immediately if its supporting state changes. Displays have no collision and never save to disk. No ground blocks are removed, replaced, or restored; the ripple is a temporary visual duplicate.

Only loaded chunks are sampled; no chunk is forced to load. Pending waves clear on level unload and server stop. Displays clean themselves up independently and are not persisted across chunk unload or restart.

## Concussion

As the front reaches a living entity, the server checks its horizontal distance, real grounded state, dry connected supporting column, and feet height. The caster, airborne entities, mounted entities, spectators, creative/invulnerable players, liquid-supported targets, and entities outside the radius are excluded. A target is processed only once per landing, rather than once per displayed block.

Concussion lasts sixty ticks (three seconds). It reduces movement speed and jump strength to zero and suppresses horizontal velocity on both the server and client while preserving vertical velocity. Mob navigation stops, but AI, attacks, and gravity are not disabled. Removing the ground still allows a concussed target to fall. Vanilla effect expiry/cure removes only this effect's attribute modifiers, preserving unrelated buffs and debuffs.

Each affected target receives two points (one heart) of base physical damage, attributed to Terragolem. Normal armor, invulnerability, PvP, and damage-event rules still apply. After damage, each surviving target receives a vertical launch velocity of 0.38 blocks per tick, giving roughly one block of rise with vanilla gravity and drag. Horizontal motion remains locked, and normal gravity returns the target to the ground. The jump-strength modifier prevents additional player/mob jumps while concussed. Low ceilings and existing gravity modifiers retain their normal physics. The status uses a vanilla slowness icon and the displayed name Concussion. Three vanilla critical-hit particles orbit above each affected head at 0.36 radians per tick, with a gentle 0.10-block vertical bob on a separate cycle, following the target as it rises or falls. Each has two smaller critical-hit points lagging along the same arc, producing short, tapered trails. The client maintains only nine particles per visible target, refreshes them before they fade, and removes them immediately on effect expiry/cure, death, tracking-range exit, or world unload. Vanilla effect packets synchronize concussion to tracking observers on application and refresh, on removal/expiry, and when a new observer starts tracking. No per-tick particle packets are needed for the head orbit.

## Verification

Development GameTests cover threshold/radius scaling and cap, liquid/waterlogged rejection, wrong-gene rejection, real six-block movement-driven landing detection with vanilla fall accumulation reset, outward display timing, exact block-state copying, upward interpolation, no-save/cleanup behavior, terrain preservation, grounded-only selection, one-time mob damage, caster/range/air/liquid exclusions, motion/jump suppression, expiry, and preservation of other effects. Existing spore, heal-light, Gust, laser, and Infernal regressions remain in the full suite. Graphical ripple smoothness still needs an in-game check.


Gradle build, Infernal regression checks, and all 54 required headless GameTests passed. Coverage includes an exact six-block fall with vanilla fall distance reset during flight, brightness metadata, block-matched dust pillars, persistence beyond two seconds, expiry after three seconds, effect synchronization to a new observer, and a live mob physics test confirming roughly one-block lift followed by landing while still stunned. In-game appearance of the lighting, dust and orbit still needs graphical verification.





