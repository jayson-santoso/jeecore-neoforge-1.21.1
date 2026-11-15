# Nimbus glide and Caelus removal plan

Status: glide and Caelus removal implemented and verified.

## Implemented and verified

- Removed the glide manager, resource/recharge state, toggle/state/boost payloads,
  glide HUD/trail/boost renderers, and shared client/server/lifecycle references.
- Removed the Gradle dependency, required metadata entry, and tracked Caelus JAR.
  Updated the README. Network protocol is now version 2; both sides must update.
- Old NBT flight fields are ignored and dropped on save. JeeCore's saved flight
  modifier is removed across available attributes without depending on its former API.
- `build runGameTestServer` passed all 67 required GameTests without Caelus,
  including old-NBT compatibility, targeted modifier cleanup, and Nimbus passive ticking.
  Existing dodge, Gust, other-gene tests, and regression checks also passed.
- Inspected the packaged mod: no obsolete glide classes or Caelus requirement remain.
  In-game client appearance and vanilla elytra controls still need manual playtesting.

## Result

Nimbus keeps Windrunner/Nimble, charge-based dodges, dash, charged Gust, its current
fall-damage protection, and sneaking slow fall. Remove the gene's built-in glide,
resource recharge/depletion, glide boost, HUD, visual effects, and flight permission.
JeeCore should build and run without Caelus. Ordinary equipped elytra remain vanilla;
retain normal `isFallFlying()` exclusions where they protect existing ability behavior.

## Implementation sequence

1. Remove the glide server and state path.
   - Delete `gene/nimbus/server/NimbusGlideManager.java`.
   - Remove its per-tick call in `NimbusPassiveHandler`, initialization and attribute
     handling in `GeneSelectionPayload`, and login/clone/gene/lifecycle hooks in
     `GeneEventHandler`.
   - Remove glide fields, accessors, copy/reset logic, and `glideToggled`/
     `glideResource` NBT serialization from `PlayerGeneData`. Old saves can ignore
     those obsolete keys and drop them on their next save, preserving other gene data.
   - Check old players' saved attribute modifiers. If necessary, strip only the
     former `jeecore:nimbus_glide` modifier (confirmed in `NimbusGlideManager`),
     without importing Caelus or changing vanilla flight/equipment state.

2. Remove toggle/state/boost networking and input.
   - Delete `ToggleGlidePayload`, `GlideStatePayload`, and `GlideBoostEffectPayload`;
     remove their registrations/imports from `NetworkHandler`.
   - In `ClientSetup`, remove the glide toggle dispatch while retaining the shared
     toggle key and Terragolem repair-menu action. Drain irrelevant clicks harmlessly.
   - Remove the glide-to-boost branch and resource-spending boost method from
     `NimbusAbilities`. Preserve ordinary dash/Gust behavior and vanilla elytra checks.
   - Review the channel protocol version: removed required payloads mean client and
     server must update together. Reject incompatible old versions clearly.

3. Remove only glide-specific client visuals.
   - Delete `GlideBarRenderer`, `GlideBoostRenderer`, and `GlideTrailRenderer`.
   - Remove the glide GUI layer from `ClientEventHandler`.
   - Remove glide shake/FOV/render/tick/cleanup paths in `BeamRenderer`, the boost
     overlay branch in `DashOverlayRenderer`, and glide cleanup in `NimbusGustClient`.
   - Remove the glide chest-equipment hiding/restoration logic in `NimbusRenderHandler`;
     keep its independent dodge/dash cloud-body handling.
   - Review Windrunner HUD spacing and stale glide references in comments, feature
     documentation, translations, selection UI, and tooltips. Do not remove shared
     textures or renderers still used by dash, dodge, Gust, or another gene.

4. Remove Caelus and update documentation in the same change.
   - Remove `implementation files("libs/caelus-neoforge-7.0.1+1.21.1.jar")` from
     `build.gradle` and the required `modId="caelus"` block from the metadata template.
   - Remove the obsolete tracked jar in `libs` after checking no other code uses it.
     Regenerate metadata through Gradle; do not hand-edit generated build outputs.
   - README: delete the local Caelus-jar prerequisite; change the Nimbus repository
     description to `Windrunner, dodges, dash, and charged gust`; remove/update the
     `libs/` entry if no local dependencies remain. Add a short Nimbus gameplay
     description and note that the new version no longer requires Caelus.
   - Keep the current README accurate until removal is actually implemented.

## Verification

- Search production sources, metadata, build settings, and README for Caelus imports,
  deleted class names, glide resource/toggle keys, and obsolete ability descriptions.
- Run `build` and the existing regression and Minecraft GameTest suites.
- Add focused tests for loading old gene NBT with obsolete glide keys and for normal
  Nimbus selection/login/respawn without Caelus. Verify Terragolem's toggle action,
  dash, Gust, Nimble/dodges, no-fall-damage, and sneaking slow fall still work.
- Launch a client and dedicated server with Caelus absent, and inspect the built jar's
  metadata/resources for remaining required Caelus references or deleted classes.
- Confirm `.github/workflows/build.yml` still builds on a fresh Linux checkout with
  no local Caelus jar; its current workflow runs the Gradle wrapper with JDK 21.
- Playtest: glide/recharge bar is absent, the old toggle never enables built-in flight,
  primary ability no longer creates glide boosts, and equipped elytra still behave
  normally. Check death, dimension travel, gene changes, and old saved players.

## GitHub delivery

The configured remote is `https://github.com/jayson-santoso/jeecore-neoforge-1.21.1.git`.
The user requested pushing all pending changes from the current `main` checkout and
leaving it clean. Include the completed Nimbus gameplay, visuals, icon, artwork,
dependency removal, and README changes; preserve newer upstream README edits and
push without force. Local caches, logs, test worlds, build outputs, and local test
source directories retain their existing ignored status. Verify remote and local
commit IDs match and `git status` is clean after delivery.
