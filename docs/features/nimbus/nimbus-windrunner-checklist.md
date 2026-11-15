# Nimbus Windrunner playtest checklist

Use Nimbus in Survival. Start with a flat running area, then test with a zombie,
skeleton, and creeper. Compare movement with the same sprint input and no other
movement effects. Test repeated attacks both inside and after each 1.2-second dodge window.

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
- [ ] Only successful damaging melee/projectile hits reduce momentum.
- [ ] Empty arm swings, punching grass/blocks, missed attacks, and firing arrows without hitting are free.
- [ ] Landed hits briefly interrupt rebuilding, even while sprinting; missed attacks do not.
- [ ] Unprotected damage does not apply an extra momentum penalty; dodging spends one stage.
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
- [ ] Nimble I / II / III provide exactly 1 / 2 / 3 guaranteed dodges.
- [ ] Hits separated by at least 1.2 seconds consume III -> II -> I -> uncharged.
- [ ] Repeated hits inside the same 1.2-second window are nullified without spending extra charges or momentum.
- [ ] Follow-up hits never restart or extend the window; the next hit at/after its end can spend another stage.
- [ ] Starting from 100 momentum still grants only three dodge windows; attacks after the final window deal damage.
- [ ] Each dodge immediately removes exactly one highlighted stage/bar and lowers the status effect amplifier.
- [ ] Zombie melee hits can be dodged.
- [ ] Skeleton arrows can be dodged.
- [ ] Creeper explosions can be dodged, including their knockback.
- [ ] Dodging a creeper does not protect surrounding blocks or other nearby entities.
- [ ] Successful dodges preserve health while spending one Nimble stage and its partial progress.
- [ ] Spending the final stage still protects for its full 1.2 seconds; afterward, attacks deal normal damage.
- [ ] The next charge can trigger immediately when the previous window ends, without a probability roll.
- [ ] Standing within zombie melee range does not spend multiple charges during one cloud-form window.
- [ ] Sprinting rebuilds dodge charges as before.
- [ ] Ownerless environmental damage does not trigger a dodge.
- [ ] Self-inflicted damage does not trigger a dodge.

## Removed glide system

- [ ] Client and server start without Caelus installed.
- [ ] Nimbus has no glide/recharge bar or glide toggle; the old toggle key does nothing for Nimbus.
- [ ] Nimbus primary still dashes normally and never triggers the removed glide boost.
- [ ] Equipped vanilla elytra and fireworks still work normally, with chest equipment rendered correctly.
- [ ] Existing saves retain their gene and other resources; old flight permission is removed on login.
- [ ] Gene changes, respawn, and dimension travel do not restore built-in flight or the old meter.
- [ ] The shared toggle key still opens Terragolem's repair menu.

## Dodge visuals and sound

- [ ] A successful dodge plays a clear wind deflection/slide sound.
- [ ] Dodging no longer emits the old cloud-particle spray from the feet.
- [ ] In third person, the body briefly becomes a pale cloud silhouette.
- [ ] Three humanoid cloud afterimages appear behind the dodge position and fade quickly.
- [ ] The normal player body and hands return after about 1.2 seconds, matching the protection window.
- [ ] First person hides the body/echoes while showing low sprite smoke and playing the dodge sound.
- [ ] Each dodge emits four cloudburst sprites: a mirrored forward/backward pair on each side, forming a butterfly-wing alignment from the feet.
- [ ] Facing north/south emits east/west; facing east/west emits north/south, including when moving in another direction.
- [ ] Dodge wings tilt outward 60 degrees from the ground, with wider faint overlays, slight mirrored forward/backward slides, and original untinted colors.
- [ ] Dodge smoke plays 25% faster and fades over about 1.76 seconds; the cloud body ends after 1.2 seconds.
- [ ] Follow-up protected hits do not replay the dodge sound, sprites, cloud form, or HUD demotion.
- [ ] Spending the final Nimble stage still shows the full sprite fade; subsequent dodges emit separate four-wing bursts.
- [ ] Nearby observers see the same sideways bursts; death and changing worlds clear the effect.
- [ ] Dodging does not teleport you or apply an Invisibility potion.
- [ ] Dash visuals still work correctly immediately before or after a dodge.

## Sprite-based angled foot trails

- [ ] The supplied wind sprite is visible instead of the old solid procedural strokes.
- [ ] The sprite background is transparent; no black rectangle surrounds it.
- [ ] Pixel edges stay sharp without blurred texture filtering or neighboring-frame bleed.
- [ ] The seven poses cycle forward without a back-and-forth ripple animation.
- [ ] Each foot has one crisp main trail with a faint, wider copy behind it; the two feet retain different animation phases.
- [ ] Nimble I starts 50% wider than before and short (1.5 blocks long, 0.27 wide).
- [ ] Nimble II stretches farther and wider (2.7 blocks long, 0.37 wide).
- [ ] Nimble III is longest and thickest (4.0 blocks long, 0.49 wide).
- [ ] Promotions and demotions blend the dimensions instead of snapping the trail size.
- [ ] Viewed from behind, trails angle outward 30 degrees from horizontal, forming a mirrored V.
- [ ] The trail centers are slightly farther apart (0.40 blocks); both layers clear the floor without clipping or z-fighting.
- [ ] Airborne and stair sections still follow diagonal changes in foot height.
- [ ] The texture follows actual turns with sharper straight sections, without fluid spline curling or camera-facing rotation.
- [ ] Running, jumping, and normal falling all leave sprite trails.
- [ ] Jump trails use short, deliberately angular sections rather than a smoothly curling fluid arc.
- [ ] Takeoff, the jump apex, and landing stay connected without an abrupt cutoff.
- [ ] Height changes are joined by visible diagonal sections rather than disconnected horizontal slices.
- [ ] Jumping straight up still renders a strip instead of collapsing its width.
- [ ] Older corners remain stable while new sections are added; they do not slide or wobble each tick.
- [ ] The main trail is brighter and slightly more opaque, with a faint 1.4x-width underlay. Both progressively fade to transparent at the far tip.
- [ ] The shorter trails keep the original widths and transparent sprite background.
- [ ] Stopping ends emission and remaining sections fade within about 1.0–1.4 seconds.
- [ ] Turning in place does not emit new sections; resuming does not connect across a pause.
- [ ] Dash, vanilla elytra flight, and dodge cloud form suppress new passive trails.
- [ ] Looking down in first person shows the ground-facing trails without a billboard blocking the view.
- [ ] Losing Nimble, death, gene changes, dimension changes, and large teleports clear trail state.
- [ ] Other players see the correct stage and animation, including when entering viewing range.
- [ ] Appearance remains stable at different frame rates.

## Subtle three-bar HUD

- [ ] There are exactly three small bars with no text, numerals, wind glyphs, or extra meter.
- [ ] The bars fill progressively toward I, II, and III.
- [ ] Earning a stage highlights the corresponding bar; losing it removes that highlight.
- [ ] Promotion/demotion briefly flashes the affected bar.
- [ ] Empty bars are faint; decay has a muted tint.
- [ ] The HUD sits comfortably at your preferred GUI scale; the old glide meter is absent.
- [ ] F1 hides the bars.

## Existing stage feedback

- [ ] Every Nimble promotion retains the rising breeze/chime and plays the supplied cloudburst sprite once, beside both legs.
- [ ] I/II/III bursts become successively larger (65%/82.5%/100% of the full size).
- [ ] All burst stages are 15% larger than before; III reaches about 2.70 blocks long and 1.04 wide per leg.
- [ ] Promotion smoke uses the original PNG colors with no added cool tint or brightness boost; running trails keep their existing brightness.
- [ ] Both promotion smoke panels tilt outward 60 degrees into a V, with a wider faint overlay behind the main smoke.
- [ ] The background is transparent and pixel edges stay crisp, with no neighboring sprite rows showing.
- [ ] Burst direction follows actual movement, including sideways running; stationary promotion falls back to facing.
- [ ] Smoke appears quickly (0.1 seconds), expands backward, and slowly fades out over 2.2 seconds.
- [ ] The seven visible poses play forward once without looping or abruptly disappearing on the blank final row.
- [ ] Smoke stays near the promotion position and drifts backward slightly as Nimbus runs onward.
- [ ] The old procedural promotion puffs/cores are gone; dash clouds still work normally.
- [ ] Nearby observers see the burst; entering viewing range or refreshing a stage does not falsely replay it.
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
- [ ] Players have independent momentum, stages, and dodge charges.
- [ ] Nearby players see promotion bursts, the flowing foot-streak trail, dodge cloud form, and hear sounds.
- [ ] A player entering viewing range sees the current trail without a false promotion burst.
- [ ] Movement remains responsive without rubber-banding on a server.
