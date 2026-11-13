# Nimbus directional Gust

Hold the existing secondary binding (V by default) to charge Gust. Release early to fire at the current charge; holding for 24 ticks / 1.2 seconds automatically fires at full charge. A quick tap produces the baseline strength, now reduced by 30%. One press produces one cast; continuing to hold after automatic release does not restart charging. The existing 110-tick / 5.5-second secondary cooldown starts when the gust fires.

## Direction and force

Gust selects living entities inside an eight-block range and a 120-degree forward cone, using Nimbus's current aim when released. Behind/side targets and targets beyond the range are excluded. Solid blocks obstruct the effect. The cone follows pitch as well as yaw.

The original distance curve is retained: `sqrt(1 - distance / 8)`. Baseline horizontal force is 1.75 times this falloff, directed radially away from Nimbus; baseline upward lift is 0.875 times the falloff. Both are 30% lower than before. Charge scales both by `1 + 0.75 * charge`, from 1.0 to 1.75. Full charging increases force without extending reach. Existing push trails and velocity synchronization remain. Nimbus and affected targets are extinguished; rear/occluded entities are not.

## Charge and visual feedback

Tracking players and the caster receive entity-specific charging-state synchronization; HUD and FOV feedback apply only to the caster. While charging, the secondary HUD box displays only a rising blue fill, keeping the normal white border and Nimbus colors. There is no charge label, percentage, or border pulse. Release returns it to its normal cooldown display; cancellation returns it to ready. HUD progress interpolates between ticks using the server's charge start time.

Activation adds a brief camera jolt. Sustained camera shake ramps from subtle to moderate over the charge, and release adds a stronger impulse proportional to charge and proximity (fading out four blocks from the release). Camera offsets do not change the server aim direction. Charge sound uses a quiet Breeze inhale; release uses a stronger/lower wind-charge burst as charge increases.

Release draws a pale-blue/white spherical section inside the complete 120-degree forward cone. A faint shell, rotating curved bands, and narrow meridian streaks make its depth visible from both the caster and outside. Charge increases shell brightness, band thickness/count (two to five), and streak count (five to six). Curved bands are thicker even on low charge, with lower opacity and gentle waviness. Their brightness tapers into separated arcs rather than complete circles; brighter core sections now have higher opacity while the edges retain their fade. high charge adds stronger waviness and brighter, thicker arcs. Shell and meridian opacity use the original brightness; the curved bands stay separated into arcs. Each shock front uses one outer rim instead of three stacked circles. Rim opacity and thickness are raised moderately so the fast aftereffects remain visible. Bands, meridian streaks, and shockwave bands have varied widths for an irregular wind texture. The main shell expands over twelve ticks with an easing motion; one to two faint delayed outer shock rims cross the same eight-block range in five ticks, overtaking it. All geometry remains inside the original cone and range, follows the server's release aim, and expires within sixteen ticks. No rear hemisphere is drawn. Timing uses world game time rather than wall-clock time, and visuals clear on world unload.

FOV squeezes quickly over the first seven ticks toward 0.82, deepening to 0.70 at full charge. Confirmed release ramps to a 1.20-1.32 multiplier over 1.5 ticks, then returns to normal over six ticks. Smoothstep easing keeps the faster jolt continuous. Cancellation settles back over five ticks without a release jolt. The release packet identifies the caster so observers do not receive this FOV effect. These multipliers compose with existing ability FOV effects and reset on world unload.

During charging, three to five pale-blue/white square wind rings wrap around the body, inspired by the Breeze silhouette. Each has four straight sides, 0.5-1 texture pixels of extruded depth with top/bottom and shaded inner/outer walls, varied width/height, an independent tilted plane, a different rotation phase and speed, and alternating spin direction. Charge increases brightness, spin speed, ring count, and tilt wobble. The initial three rings fade in and grow from a smaller footprint and thin strip to full width over six ticks. Two additional rings enter at six-tick intervals, each with its own growth/fade-in. Per-ring width varies by roughly threefold with a gentle pulse. Ring depth is halved while the width and square footprint stay unchanged. Opacity drops smoothly by up to 28% at each side endpoint/corner. Rings expand/fade out over five ticks when released or cancelled. The local first-person view shows only the lower portions so the camera remains clear; third-person and observers see the full body animation. Rings follow interpolated player positions and clear on world unload, invalid entities, gene changes, or stale charge state.

Release also emits eight to fourteen fast vanilla cloud particles from nearby floor surfaces found by downward collision rays. Their velocities fan forward with a small upward component relative to the aiming direction. When no floor is found within four blocks, that particle is skipped.

## Input and lifecycle

Server state owns charge duration, release strength, cooldown, and hit selection. Press/release/cancel/hold-update payloads report input, never client-computed strength or target lists. Hold updates arrive every ten ticks; loss for over twenty ticks cancels rather than leaving a stuck charge. Menu/focus loss cancels locally. Death, disconnect, gene changes, dimension changes, respawn/entity replacement, and server stop invalidate or clear the charge. Aborting does not spend the secondary cooldown. Other genes retain their existing secondary input behavior.

## Verification

Headless cases cover arc/range exclusion, the original distance curve, full-charge scaling, directional extinguishing, arc boundary, wall obstruction, early release, cooldown, automatic single release, release-time aiming, cancellation/gene changes/missing hold updates, and directional payload round trips. The existing spore, heal light, Terragolem, and Infernal regressions remain in the suite. Camera feel, HUD readability, and spherical cap appearance require graphical playtesting.

Validation includes Gradle build, the Infernal regression checks, and the 47 required GameTests, including horizontal/lift reduction and caster identity in the release payload.



The square-ring and visual-density revision passes Gradle build and the Infernal regression checks. It changes client rendering only; the last gameplay run passed all 47 required GameTests.




## Glide boost wind wake

The old pair of circular boost rings is removed. A backward-facing spherical section now shares the Gust renderer, including its curved wind bands and fast delayed shockwave rims, with a 5.5-block visual reach and a sixteen-tick lifetime. Twelve vanilla cloud particles shoot backward in a narrow fan from behind the launch position. They replace the old nondirectional cloud puff; existing flash, boost trail, shake, FOV, sound, resource cost, and movement behavior remain.

The server includes launch position and look direction in the boost visual packet. Clients use the negative of that captured direction for both the curved wake and clouds, including when boosting vertically. Looking elsewhere after launch does not rotate the existing wake. World unload clears boost bursts and their tracked visual state. The payload regression checks exact origin/direction round trips.
