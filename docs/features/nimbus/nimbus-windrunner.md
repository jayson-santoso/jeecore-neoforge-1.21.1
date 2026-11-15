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
| Unprotected health damage | No extra momentum penalty or rebuilding interruption |
| Dodging an eligible incoming attack | Consume one Nimble stage and its momentum bar |
| Landed damaging melee/projectile hit | -10, at most once per half-second |

A landed damaging hit interrupts rebuilding for half a second. Empty swings,
mining/grass interaction, missed attack attempts, and fired projectiles that have
not hit an entity do not spend momentum. Continued sprinting during

that interruption drains at the stopped rate. Taking damage does not interrupt buildup.

There is no five-second decay grace period. A successful dodge consumes one stage and opens a fixed 24-tick protection window, without a probability roll.

Sprinting must actually move the player horizontally; sprinting into a wall or

jumping in place cannot charge. Gliding, swimming, climbing, riding, creative flight,

and Nimbus dash do not charge. These excluded movements drain momentum instead.

Momentum is server-owned and capped at 100. Logout, death, dimension changes, gene

removal, or a position discontinuity over eight blocks reset it. The owner receives

updates every five ticks and immediately on stage changes or combat losses.

## Nimble

| Status | Enter at | Demote below | Ground speed | Air acceleration | Air speed ceiling | Banked dodges |
| --- | --- | --- | --- | --- | --- | --- |
| Nimble I | 25 | 20 | +5% | +15% | +10% | 1 |
| Nimble II | 50 | 45 | +10% | +30% | +20% | 2 |
| Nimble III | 75 | 70 | +15% | +45% | +30% | 3 |

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

The effect uses a transparent 18x18 pixel cloudburst icon derived from the supplied
smoke sprite, replacing the previous wind icon at the same texture path.

Demotion immediately replaces its amplifier and ground-speed modifier.

## Dodge and cloud echoes

An incoming attack attributed to another living entity is guaranteed to be dodged
while Nimbus has a Nimble stage. This includes melee, living-owned arrows/projectiles,
and creeper explosions. Ownerless environmental damage and self-inflicted damage do
not spend dodge charges. Uncharged, creative, spectator, and dashing players do not
use this passive dodge.

Each new dodge immediately consumes exactly one stage: III -> II -> I ->
uncharged. Momentum becomes 50, 25, then 0 respectively, and the corresponding HUD
bar disappears immediately. Partial progress and the 75–100 reserve are spent along
with the stage, so a full meter still provides exactly three charges, never four.
Each dodge opens a fixed 24-tick (1.2-second) attack-nullification window. Further
qualifying living-entity attacks during it are canceled without consuming momentum,
replaying dodge sounds/visuals, or extending the deadline. A new dodge may spend
another stage only when that window ends. The last charge also protects for the
full window; after it ends, attacks damage an uncharged Nimbus normally.

Dodged explosions also lose their knockback for that player, while still affecting
surrounding blocks and other entities. This also applies to explosions blocked
for free during an active protection window.

A wind deflection/slide sound accompanies the dodge. There is no longer a vanilla

cloud-particle spray from the feet. For 24 ticks (1.2 seconds), the player's visible body and hands are

replaced by a pale cloud silhouette; three humanoid cloud echoes linger behind the

dodge position and fade out. The cloud body and echoes are visible in third person

and to nearby observers; first person keeps the view clear and uses sound.

Cloud form is a visual representation of the protection window, not an invisibility
potion or teleport. Ownerless environmental damage and self-inflicted damage remain
outside the dodge's attack protection, including during cloud form.

A dash has its own separate visual state and remains independent.

Every successful dodge also emits four mirrored cloudburst sprites from the center
of the feet. They point perpendicular to the facing yaw sent by the server (north
facing emits east/west), regardless of movement or attack direction. The panels use
an outward 60-degree tilt and the same faint 1.4x-width, 22%-opacity underlay as promotion smoke.
Each lateral side has a forward/backward mirrored pair, forming four butterfly-like
wings with their inner edges hinged near the feet. Each root drifts outward 0.70
blocks, with a slight mirrored forward/backward translation peaking at 0.12 blocks.
The one-shot sprite and fade run 25% faster than promotion smoke,
finishing in about 1.76 seconds. The cloud body/echoes last 1.2 seconds, matching protection.
Dodge smoke remains visible after spending the final Nimble stage and renders in
first person and for observers. Repeated dodges emit separate four-wing bursts; only old expired
bursts are pruned, with a bounded queue. Death, player removal, explicit dodge reset,
and world changes clear them. The original PNG colors are used without a tint.

## Sprite-based foot trails

The previous procedural strokes are replaced by the player-supplied `trail-sprite.png`,
copied unchanged to `assets/jeecore/textures/effect/nimbus_wind_trail.png`. It already
contains real transparency and seven vertically stacked 64x16 frames (64x112 total).
The custom renderer selects frame UVs directly; no atlas animation metadata is needed.
Nearest-neighbor filtering preserves the original pixel edges. The source artwork's
unused transparent padding is cropped through UVs, without modifying the PNG.

Each foot has one main sprite strip with a faint, wider underlay. The two feet have stable UUID-seeded starting
phases and subtly different playback speeds. The
nominal loop is `0,1,2,3,4,5,6`, two ticks per pose (0.7 seconds), with
playback rates varying by +/-6%. The poses advance forward instead of ping-ponging, with desynchronized feet.

| Stage | Primary strip length | Primary strip width | History fade |
| --- | --- | --- | --- |
| I | 1.5 blocks | 0.27 blocks | 1.0 seconds |
| II | 2.7 blocks | 0.37 blocks | 1.2 seconds |
| III | 4.0 blocks | 0.49 blocks | 1.4 seconds |

Promotion and demotion blend strip dimensions over eight ticks per stage. The main
strip per foot reaches peak opacity 95% while moving quickly. A wider copy is drawn
first at 1.4x width and 22% of the main strip opacity, using the same animation and
tip gradient. A modest 8% RGB boost brightens both layers without modifying the PNG. Actual horizontal
speed scales length from 55% to 100% of its stage maximum and brightness from
55% to 100%; steady running no longer adds a sinusoidal length/opacity wobble.
Stepped highlight bands travel backward along the strip to suggest rushing air.
A separate smooth spatial gradient fades the strip from full strength near the
feet to zero at the far tip, multiplied by its existing age fade. The PNG supplies
the wavy silhouette and taper. Normal alpha blending preserves the
source transparency and colors.

New samples allow normal movement both on the ground and in the air, including
the brief stationary instant at a jump apex. The strips follow recorded turns
with short straight sections, avoiding fluid spline overshoot and curling. Historical points retain their real foot heights. Older corners use fixed two-tick
samples and straight joins for an intentionally choppy, faceted arc rather than a
smooth fluid curve. The ribbon preserves the Y coordinate of each endpoint: it is
tilted outward 30 degrees across each foot, forming a mirrored V during level running,
and follows diagonal height changes during jumps, falls, and stairs. Trail centers
are 0.40 blocks apart (previously 0.30). Nimble I starts 50% wider, with the same
additional width per stage. Both layers share a small lift to keep the tilted inner
edges above the floor. Tiny overlaps close seams between sections; a fallback
width keeps pure vertical jumps from collapsing to a zero-width strip.
Takeoff, apex, and landing remain connected. Brief slowdowns or block-collision stalls up to four ticks preserve the path;
real pauses and teleport-sized movement still break it. Small movement samples use
a 0.025-block threshold so brushing obstacles does not repeatedly clear the trail.

Stopping leaves the existing trail to fade without extending it,
with a fast six-tick (0.3-second) fade after a three-tick grace rather than a lingering
water-like wake. Spatial tip fading remains in effect while moving.
Dash, vanilla elytra flight, dodge cloud form, water, climbing, riding, and creative flight also
suppress new samples. Teleports, death, dimension changes, and losing Nimble clear
visual state. First-person looking down, third person, and observers see the same
height-following sprite effect. Momentum buildup and other Nimbus abilities are
unchanged by these visual trail rules.

## HUD and stage feedback

Only three small bars are drawn, with no labels, numerals, extra meter, or panel.

The first fills from 0–25, the second 25–50, and the third 50–75. An earned stage

highlights its bar; all three are highlighted at III. Momentum from 75–100 is a

reserve above the final stage rather than an additional HUD element. Decay has a

muted tint; an empty HUD is faint. Promotion/demotion briefly flashes the affected bar.

Every promotion keeps the rising breeze/chime and plays the supplied transparent
`cloudburst.png`, copied unchanged to `textures/effect/nimbus_cloudburst.png`, once
beside each leg. This replaces the procedural promotion puffs; dash visuals are unchanged.
The 64x256 sheet has seven visible 64x32 poses and a blank final row. Fixed UV bounds
preserve the artwork's expanding silhouette; the blank row is skipped so opacity can
fade smoothly rather than disappear abruptly. Frames begin at ticks 0/2/5/10/17/25/34.
The burst reaches peak opacity in two ticks (0.1 seconds), then fades over its total
44-tick (2.2-second) lifetime. It expands quickly in the first six ticks and drifts
backward 0.45 blocks while fading at the promotion location.

Both smoke panels tilt outward 60 degrees, forming a steeper V beside the legs.
A faint copy at 1.4x width and 22% opacity sits behind each main sprite, with a shared
lift keeping the inner edges above ground. The burst points backward along horizontal
movement, falling back to facing when stationary. Nimble I/II/III scale its length and
width to 65%/82.5%/100%, with an overall 15% size increase; at III its peak main
dimensions are about 2.70 blocks long and 1.04 blocks wide per leg. Burst rendering
uses a neutral RGB multiplier of (1.0, 1.0, 1.0), with no cool tint or inherited
trail brightness boost, preserving the original sprite colors.
The source PNG remains unchanged. Nearby observers see the same one-shot animation; stage sync
without a promotion does not replay it. Death, teleports, loss of Nimble, and world
changes clear the burst. Vanilla promotion/demotion clouds and

passive airborne cloud particles have been removed. A stronger +4/+6/+8 degree

FOV swell lasts 0.8/1.0/1.2 seconds, easing smoothly into and out of its peak.

The lighter, near-white nausea-pattern mist lasts 0.4/0.5/0.6 seconds at peak opacity

22%/29%/36%, with a clear center. Both effects finish their own animation even if

a demotion occurs mid-pulse, preventing an abrupt FOV cutoff.

Demotion uses a lower breeze and dispersing particles without the promotion pulse.

Minecraft's FOV Effects and Distortion Effects sliders scale their corresponding

pulses. No gameplay Nausea effect is applied. Initial sync and resets do not pulse.

## Verification

`gradlew check` covers progression, losses, charge consumption, protection-window boundaries, and air-control math.

Development GameTests cover sprint/walk/sneak/stop transitions, damage preserving momentum and sprint rebuilding, effect replacement

and ground-speed cleanup, attack/projectile losses, melee/arrow/explosion dodges,

explosion knockback, the cosmetic cloud form's lack of invulnerability, directional

custom promotion payloads, observer synchronization, and removal of vanilla cloud

particle emissions.

Visual layout, sound balance, and actual movement feel still need a client playtest.

See [the playtest checklist](nimbus-windrunner-checklist.md).

