# Nimbus Windrunner playtest checklist

Use Nimbus in Survival. Start with a flat running area, then test with a zombie,
skeleton, and creeper. Compare movement with the same sprint input and no other
movement effects. Dodges are random: test repeated attacks at each stage.

## Simple momentum

- [ ] Sprinting on flat ground steadily charges momentum without any special maneuvers.
- [ ] Sprint jumping charges too, without needing gaps, walls, or specific landings.
- [ ] Uninterrupted sprinting reaches I in about 4.2 seconds, II in 8.4, and III in 12.5.
- [ ] Continuing to sprint caps momentum at 100.
- [ ] Walking immediately drains momentum slowly.
- [ ] Stopping drains it faster than walking; there is no five-second grace period.
- [ ] Sneaking drains it faster than stopping, even if still moving.
- [ ] Sprinting into a wall or jumping in place cannot charge.
- [ ] Returning to active sprinting rebuilds momentum.
- [ ] Melee attacks, missed swings, and firing arrows reduce momentum.
- [ ] Attacking briefly interrupts rebuilding, even while sprinting.
- [ ] Taking damage does not remove momentum or interrupt sprint rebuilding.
- [ ] Multishot projectiles do not multiply one attack's momentum penalty.

## Nimble stages and ground speed

- [ ] Nimble I appears at 25 momentum, II at 50, and III at 75.
- [ ] The inventory shows the correct name, numeral, and wind icon.
- [ ] Demotions occur below 70 (III to II), 45 (II to I), and 20 (I to uncharged).
- [ ] Hovering near a threshold does not repeatedly promote and demote.
- [ ] Uncharged movement is normal; I / II / III give +5% / +10% / +15% ground speed.
- [ ] Both walking and sprinting benefit from the stage's speed bonus.
- [ ] Landing and continuing to run feels comfortable.
- [ ] Demotion reduces ground speed; losing Nimble restores baseline speed.
- [ ] Maintaining a stage does not stack more speed bonuses.
- [ ] A normal Speed potion still works; removing Nimble leaves that potion active.

## Air control and exclusions

- [ ] Higher stages improve horizontal air acceleration, speed, steering, and braking.
- [ ] Camera movement alone does not steer; directional input does.
- [ ] Opposite input brakes before reversing; releasing input lets you coast.
- [ ] Diagonal or sustained input does not produce runaway speed.
- [ ] Air handling blends through stage changes instead of snapping.
- [ ] Gravity, jump height, and existing sneak slow fall still behave normally.
- [ ] Gliding and dash retain their existing handling and do not charge momentum.
- [ ] Swimming, climbing, riding, and creative flight do not charge momentum or activate custom air control.

## Dodge gameplay

- [ ] Uncharged Nimbus cannot dodge.
- [ ] I / II / III dodge approximately 30% / 50% / 60% of eligible hits over many attempts, with at least one second between hits.
- [ ] Zombie melee hits can be dodged.
- [ ] Skeleton arrows can be dodged.
- [ ] Creeper explosions can be dodged, including their knockback.
- [ ] Dodging a creeper does not protect surrounding blocks or other nearby entities.
- [ ] Successful dodges preserve health and momentum.
- [ ] Failed dodges deal normal damage while preserving momentum and the current dodge stage.
- [ ] Another successful dodge cannot occur for one second.
- [ ] Another hit can still damage you during the half-second cloud form; it is cosmetic.
- [ ] Ownerless environmental damage does not trigger a dodge.
- [ ] Self-inflicted damage does not trigger a dodge.

## Dodge visuals and sound

- [ ] A successful dodge plays a clear wind deflection/slide sound.
- [ ] Dodging no longer emits the old cloud-particle spray from the feet.
- [ ] In third person, the body briefly becomes a pale cloud silhouette.
- [ ] Three humanoid cloud afterimages appear behind the dodge position and fade quickly.
- [ ] The normal player body and hands return after about half a second.
- [ ] First person stays clear while sound communicates the dodge.
- [ ] Dodging does not teleport you or apply an Invisibility potion.
- [ ] Dash visuals still work correctly immediately before or after a dodge.

## Flowing foot-streak trail

- [ ] The passive humanoid afterimages are gone; Nimble produces long wispy streaks from both feet.
- [ ] Trails consist of connected, crisp pixel-like sections with no visible gaps between them.
- [ ] Segments nearest the feet are the thickest and brightest.
- [ ] Thick sections occupy most of the trail (40% at 3x, then 25% at 1.5x), with a short thin taper at the end.
- [ ] Corners and thickness transitions stay connected while the tail remains thin and transparent.
- [ ] The streaks ripple gently rather than looking like rigid straight lines.
- [ ] Running and jumping both leave foot wisps along the actual route.
- [ ] Turning bends the trail smoothly instead of rotating the whole trail with the camera.
- [ ] II has longer and slightly thicker streaks than I; III has the longest, thickest strokes.
- [ ] Stopping ends new emission; remaining wisps taper and fade within roughly 1.0–1.4 seconds.
- [ ] Resuming movement does not draw a sharp connector across a pause.
- [ ] Merely turning in place does not generate wisps.
- [ ] Dash, glide, and dodge cloud form do not emit overlapping new foot trails.
- [ ] Looking down in first person shows foot-level wisps without a body silhouette blocking the view.
- [ ] Losing Nimble or changing genes clears the trail and promotion smoke.
- [ ] Teleports and dimension changes do not draw a streak across the world.
- [ ] The trail remains consistent at different frame rates.

## Subtle three-bar HUD

- [ ] There are exactly three small bars with no text, numerals, wind glyphs, or extra meter.
- [ ] The bars fill progressively toward I, II, and III.
- [ ] Earning a stage highlights the corresponding bar; losing it removes that highlight.
- [ ] Promotion/demotion briefly flashes the affected bar.
- [ ] Empty bars are faint; decay has a muted tint.
- [ ] The HUD does not overlap the glide meter at your preferred GUI scale.
- [ ] F1 hides the bars.

## Existing stage feedback

- [ ] Promotion retains the rising breeze/chime and produces a custom backward cloud-smoke burst.
- [ ] The burst matches the dash cloud renderer rather than vanilla Minecraft cloud particles.
- [ ] Burst direction follows actual movement, including sideways running; stationary promotion falls back to facing.
- [ ] The dense pastel plume starts at the feet, moves slowly backward/upward, and remains visible for roughly 1.8–2.2 seconds.
- [ ] Blue, green, and pink pastel shades match the dash clouds.
- [ ] Small pulsing white cores have soft colored glows, like the dash core particles.
- [ ] Clouds spread at slower varied speeds with gentle floating motion and a longer fade; core glows last roughly 1.4–1.8 seconds.
- [ ] The old promotion spiral, demotion cloud particles, and passive airborne cloud specks are gone.
- [ ] Promotion produces a stronger, smoothly easing FOV swell and a brighter, near-white mist overlay.
- [ ] Higher stages pulse more strongly; the FOV returns smoothly over 0.8 / 1.0 / 1.2 seconds.
- [ ] The white overlay fades over 0.4 / 0.5 / 0.6 seconds and keeps the center clear.
- [ ] A demotion during a promotion pulse does not abruptly snap the FOV back.
- [ ] Demotion uses lower wind/dispersing particles without a promotion pulse.
- [ ] FOV Effects at 0% disables the FOV pulse.
- [ ] Distortion Effects at 0% disables the overlay; no actual Nausea debuff is applied.

## Cleanup and multiplayer

- [ ] Death, reconnecting, dimension changes, and gene changes clear momentum and speed bonuses.
- [ ] Cloud form/afterimages never leave the player permanently hidden after a reset.
- [ ] Large teleports reset momentum instead of charging it.
- [ ] Resets/reconnects do not trigger a false promotion.
- [ ] Players have independent momentum, stages, and dodge cooldowns.
- [ ] Nearby players see promotion bursts, the flowing foot-streak trail, dodge cloud form, and hear sounds.
- [ ] A player entering viewing range sees the current trail without a false promotion burst.
- [ ] Movement remains responsive without rubber-banding on a server.
