# Nimbus Windrunner



Windrunner builds through ordinary sprint movement, including sprint jumping. There

are no gap, terrain-proximity, landing-chain, or airborne-distance checks. Walking

is intentionally a recovery-breaking pace rather than a charging action.



## Momentum



| Action | Momentum change |

| --- | --- |

| Moving while sprinting, on ground or in the air | +6 / second |

| Walking | -4 / second |

| Stopping or excluded movement | -8 / second |

| Sneaking | -12 / second |

| Taking damage (including a failed dodge) | No momentum penalty or rebuilding interruption |

| Attacking, missing an air swing, or firing a projectile | -10, at most once per half-second |



Attacking interrupts rebuilding for half a second. Continued sprinting during

that interruption drains at the stopped rate. Taking damage does not interrupt buildup.

There is no five-second decay grace period. A successful dodge preserves momentum.



Sprinting must actually move the player horizontally; sprinting into a wall or

jumping in place cannot charge. Gliding, swimming, climbing, riding, creative flight,

and Nimbus dash do not charge. These excluded movements drain momentum instead.



Momentum is server-owned and capped at 100. Logout, death, dimension changes, gene

removal, or a position discontinuity over eight blocks reset it. The owner receives

updates every five ticks and immediately on stage changes or combat losses.



## Nimble



| Status | Enter at | Demote below | Ground speed | Air acceleration | Air speed ceiling | Dodge chance |

| --- | --- | --- | --- | --- | --- | --- |

| Nimble I | 25 | 20 | +5% | +15% | +10% | 30% |

| Nimble II | 50 | 45 | +10% | +30% | +20% | 50% |

| Nimble III | 75 | 70 | +15% | +45% | +30% | 60% |



From empty, uninterrupted sprinting reaches I in about 4.2 seconds, II in 8.4,

and III in 12.5. The five-point demotion buffer prevents threshold flickering.



The air ceiling is relative to vanilla input acceleration and standard air drag.

It limits Windrunner-generated speed, not existing sprint-jump or external impulses.

Horizontal steering removes 4% of sideways drift per stage per tick at full input;

opposite input adds 15% / 30% / 50% braking acceleration. Releasing input coasts.

Air handling blends over six ticks per stage; vertical velocity, gravity, and jump

height are unchanged. The custom air control excludes gliding, swimming, climbing,

riding, creative flight, and dash.



Nimble is a beneficial status effect with amplifiers 0–2 and Roman numerals in the

inventory. Its ground-speed modifier scales with level and does not stack on refresh.

Demotion immediately replaces its amplifier and ground-speed modifier.



## Dodge and cloud echoes



An incoming hit attributed to another living entity rolls the current stage's dodge

chance on the server. This includes melee, living-owned arrows/projectiles, and

creeper explosions. Ownerless environmental damage and self-inflicted damage do not

roll. Uncharged, creative, spectator, and dashing players do not roll.



A successful dodge cancels that hit, preserves momentum, and starts a one-second

cooldown between successful dodges. Dodged explosions also lose their knockback for

that player. The explosion still affects the surrounding world and other entities.



A wind deflection/slide sound accompanies the dodge. There is no longer a vanilla

cloud-particle spray from the feet. For ten ticks (0.5 seconds), the player's visible body and hands are

replaced by a pale cloud silhouette; three humanoid cloud echoes linger behind the

dodge position and fade out. The cloud body and echoes are visible in third person

and to nearby observers; first person keeps the view clear and uses sound.



Cloud form is cosmetic, not an invisibility potion, teleport, or invulnerability

window. Another attack can still hit during it because the dodge is on cooldown.

A dash has its own separate visual state and remains independent.



## Flowing foot wisps

The passive humanoid afterimage trail has been replaced by paired, long wavy
streaks starting at the feet. Two thin strands per foot follow a client-tick
history of the actual route, smoothly interpolated through turns and jumps.
The luminous stroke thickens slightly at I, more at II, and most at III, with
base half-widths of 0.024/0.032/0.040 blocks before tapering. The trail is built from connected, square-ended 0.20-block cells with a tiny overlap
to close seams at corners and thickness changes. Five thickness plateaus (300%, 150%, 75%, 18%, 3.5% of base width)
occupy the first 40%, next 25%, next 17%, next 12%, and final 6% of the trail.
The thick sections occupy most of the length, with half-width intermediate steps
before a short, thin tail. Stepped opacity
make the sections near the feet thickest and brightest, ending in thin, translucent
fragments. Soft mist edges have been removed; small snapped wave offsets reinforce
the Minecraft-style pixel silhouette while preserving the flowing movement path.

| Stage | Maximum path length | Sample lifetime |
| --- | --- | --- |
| I | 4.2 blocks | 1.0 seconds |
| II | 5.4 blocks | 1.2 seconds |
| III | 6.6 blocks | 1.4 seconds |

New samples require movement. Stopping leaves the existing wisps to fade; resuming
starts a new section rather than drawing a straight connector across the pause.
Dash, glide, dodge cloud form, water, climbing, riding, and creative flight suppress
new sections. Teleports, death, dimension changes, and losing Nimble clear state.
The foot-level ribbons can be seen when looking down in first person and from
third person or nearby observers. No passive humanoid silhouettes are emitted;
the separate short dodge cloud form and dodge echoes remain.

## HUD and stage feedback



Only three small bars are drawn, with no labels, numerals, extra meter, or panel.

The first fills from 0–25, the second 25–50, and the third 50–75. An earned stage

highlights its bar; all three are highlighted at III. Momentum from 75–100 is a

reserve above the final stage rather than an additional HUD element. Decay has a

muted tint; an empty HUD is faint. Promotion/demotion briefly flashes the affected bar.



Promotion keeps the rising breeze/chime and emits a backward cloud-smoke burst through

the same layered quad renderer used by Nimbus dash. Its direction follows actual

horizontal movement, falling back to facing when stationary. Higher stages emit

a wider burst, fading over 1.8/2.0/2.2 seconds. Smoke count is
26/32/38, with mostly small puffs and a slower backward/upward plume. Emission starts
at foot level (0.06–0.24 blocks high), with a narrow origin and gentle upward lift. Core glows last 1.4/1.6/1.8 seconds.
The dash's blue, green, and pink pastel palette is reused, with 5/6/7 tiny pulsing
white cores and colored glows in the additive pass. Vanilla promotion/demotion clouds and

passive airborne cloud particles have been removed. A stronger +4/+6/+8 degree

FOV swell lasts 0.8/1.0/1.2 seconds, easing smoothly into and out of its peak.

The lighter, near-white nausea-pattern mist lasts 0.4/0.5/0.6 seconds at peak opacity

22%/29%/36%, with a clear center. Both effects finish their own animation even if

a demotion occurs mid-pulse, preventing an abrupt FOV cutoff.

Demotion uses a lower breeze and dispersing particles without the promotion pulse.

Minecraft's FOV Effects and Distortion Effects sliders scale their corresponding

pulses. No gameplay Nausea effect is applied. Initial sync and resets do not pulse.



## Verification



`gradlew check` covers progression, losses, dodge odds/cooldown, and air-control math.

Development GameTests cover sprint/walk/sneak/stop transitions, damage preserving momentum and sprint rebuilding, effect replacement

and ground-speed cleanup, attack/projectile losses, melee/arrow/explosion dodges,

explosion knockback, the cosmetic cloud form's lack of invulnerability, directional

custom promotion payloads, observer synchronization, and removal of vanilla cloud

particle emissions.

Visual layout, sound balance, and actual movement feel still need a client playtest.

See [the playtest checklist](nimbus-windrunner-checklist.md).

