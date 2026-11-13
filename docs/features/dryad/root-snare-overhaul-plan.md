# Root Snare Visual Overhaul — Design and Implementation

**Status:** Visual revisions implemented, followed by the 2026-10-10 NoAI ownership/reset fix in section 14. The updated JAR built successfully. Normal world lighting remains selected. The modpack's interfering caller has not yet been identified, and the AI fix has not been exercised in-game.

**Target:** Minecraft 1.21.1 / NeoForge 21.1.228, using this project's mapped Minecraft classes.

**Workflow:** The design pause was superseded by the user's instruction to implement. Building a mod JAR and launching the development client still require separate user instructions; neither was performed for this implementation.

**Reading this document:** Sections 1–6 preserve the original proposal and pre-overhaul baseline. Sections 7 and 9 record earlier selected decisions; sections 11 and 12 record prior implementations. Section 13 describes the latest changes and supersedes their duration, withdrawal and mote settings. Section 10 retains the original lushness suggestions.

This document covers the vegetation proposal and the user-supplied animated binding model. Motes, further ground styling, and sound can receive their own proposals later. The vegetation proposal preserves the current snare radius, target eligibility, capture timing, duration, AI behavior, and effects.

## 1. Suggested visual directions

| Direction | What the player sees | Implementation implication |
|---|---|---|
| **A. Woodland patches — recommended** | Dense grass between irregular fern clusters. Greenery rises outward from the caster, with small openings around root bindings. The field gently moves while active. | Generate a stable patch layout, reveal plants when their ground ring changes, and animate each patch with a slightly different phase. Fits the existing block-display approach. |
| **B. Fern-led growth wave** | Ferns make the advancing edge easy to read. Grass fills the area immediately behind them, leaving a softer interior. | Give plants an edge/interior role and separate reveal offsets. The trailing grass must wait for transformed ground; it cannot advance ahead of the terrain wave. |
| **C. Root-centered undergrowth** | Grass fills the field, while taller fern groups frame each bound target. Dead bushes add a little brown detail near the roots. | Combine field placement with target-local placement when capture occurs. Requires careful spacing when several mobs stand together. |

**Confirmed direction:** The user selected A, including varied plant heights and orientation, growth synchronized with moss/rooted-dirt transformation, and a bouncy spawn animation. Start with A. It keeps the requested density while making the vegetation look like woodland growth rather than a uniform lawn. Borrow only the subtle target framing from C. Keep flowers and pink petals out of the palette.

The main changes would be:

- Replace independent tile-by-tile randomness with a few recognizable plant groups.
- Make the outward growth sequence more readable, with plants unfolding from the ground instead of looking like a vertically stretched finished model.
- Add gentle movement to the vegetation itself, with enough variation that the whole field does not rock together.
- Keep the brown roots readable through small, deliberate openings.
- Withdraw the plants into the soil at the end, without lingering displays.

The overall woodland direction, height/orientation variation, terrain synchronization, and bouncy emergence are confirmed. The user's later instruction authorized implementation using normal world lighting; the selected values and backend are recorded in sections 7 and 11.

## 2. Pre-overhaul implementation to preserve or improve

| Area | Current behavior |
|---|---|
| Server owner | `DryadRootSnareManager.RootSnareInstance` owns ground restoration, plant displays, and affected entities. |
| Field layout | Each eligible ground tile has an 82% planting chance; the center always attempts to sprout. Species selection is approximately 55% tall grass / 45% fern. |
| Plant models | `Blocks.TALL_GRASS` uses two displays, with `DoublePlantBlock.HALF` set to `LOWER` and `UPPER`. `Blocks.FERN` and `Blocks.DEAD_BUSH` use one display each. |
| Expansion | The center changes at tick 0; rings 1–4 change at ticks 3, 6, 9, and 12. The next ring's plants are created hidden before its ground changes. |
| Growth | Field plants grow over 6 ticks, then settle from scale 1.04 to 1.0. The center needs a 2-tick tracking lead; later rings are staged beforehand. |
| End | Retraction begins at tick 104; displays are removed at tick 120. Cancellation and unload remove them immediately. |
| Rendering | `SnareVegetationDisplay` extends vanilla `Display.BlockDisplay`. Vanilla entity tracking and `DisplayRenderer.BlockDisplayRenderer` render the plants. |
| Target accents | A few dead bushes are added on capture. Nearby field plants are currently discarded to expose the bindings. |

What remains unverified is how this looks in-game, especially packet timing, growth visibility, resource-pack models, and many simultaneous snares. Compilation alone does not establish visual quality.

The layout is dense, but independent placement can still make accidental gaps or a repetitive carpet. The current 0.8-block underground translation also keeps much of the early growth hidden. Abruptly discarding plants near newly captured targets can produce a visible pop. These are the first things the overhaul should address.

## 3. Proposed woodland layout

### Palette and silhouette

Use the vanilla baked block models and their existing pixel textures:

| Plant | Proposed role | Initial scale range, to confirm |
|---|---|---|
| `Blocks.TALL_GRASS` | Main vertical texture between fern groups | 0.55–0.70; both halves together are about 1.1–1.4 blocks tall |
| `Blocks.FERN` | Fuller, lower clusters that interrupt the grass pattern | 0.75–0.95 |
| `Blocks.DEAD_BUSH` | Small brown accents around captured targets | 0.60–0.80; usually 2 per ordinary target |

Keep the silhouettes crisp and cutout-based. Plant blocks already use crossed textured planes; there is no need to replace them with bark cuboids or glowing translucent geometry. Resource packs should supply their normal block models and textures. NeoForge's [model documentation for 1.21.1](https://docs.neoforged.net/docs/1.21.1/resources/client/models/) describes the model and render-type system.

Do not assume vanilla block displays expose an arbitrary RGB tint or alpha fade. The current vanilla `renderSingleBlock` path requests its block color without a world position, so position-specific biome color should not be promised. For the first version, variety should come from grass/fern silhouettes, scale, orientation, and grouping. Custom per-plant color is a separate renderer decision.

### Placement rules

Suggested starting values:

- Use 6–8 irregular patches across the existing ground field, with overlapping patches merged rather than spawning duplicate plants.
- Aim for roughly 80% overall eligible-tile coverage. Make patch interiors dense and leave a few small gaps between them.
- Give each patch a grass/fern bias, while keeping both species present across the field. Avoid perfect circles and evenly spaced spokes.
- Initially allow one plant group per ground tile. Tall grass counts as one plant group but two display entities.
- Apply modest sub-block jitter and stable yaw; do not reroll position or species during rendering.
- Reserve space around each confirmed bound target, sized from its hitbox. Preserve the current clearance rule as the baseline.
- If a target moves into existing foliage before capture, retract the obstructing plants briefly rather than abruptly deleting visible plants. Hidden plants may be discarded immediately.
- Share or skip overlapping dead-bush accents when targets are close together.

Create the layout once per snare using a stored random seed. Ground sampling must remain tied to the surfaces the current ability actually transforms. Avoid extending decorative coverage onto water, unsupported ledges, or untransformed upper platforms merely to complete a patch.

The layout decides **where** plants belong. The ground wave decides **when** they may emerge. Patch membership must not bypass the ring timing.

## 4. Proposed animation sequence

### Emergence

Each plant starts below its sampled ground surface. Its vertical scale and translation increase together, with its base approaching the surface. Use a shallower starting translation than the current 0.8 blocks; approximately 0.12–0.20 blocks is a proposed starting range so early growth is visible sooner.

Keep the server's current ring schedule:

| Ring | Ground transform tick | Proposed plant behavior |
|---|---:|---|
| Center | 0 | Start the field visibly; preserve the current tracking lead unless a different synchronization design is confirmed. |
| 1 | 3 | Begin growth with this ring's ground update. |
| 2 | 6 | Begin growth with this ring's ground update. |
| 3 | 9 | Begin growth with this ring's ground update. |
| 4 | 12 | Begin growth with this ring's ground update; capture timing stays unchanged. |

Stage each upcoming ring while hidden. Transform its ground first, then update its displays in the same server tick. Do not delay the terrain or capture timing simply to accommodate the visual animation.

The user confirmed a bouncy spawn. Proposed total duration: 8 ticks, with a fast rise, one modest overshoot, a small compression, then settling. The duration and amplitudes still need confirmation. Small per-patch differences should affect height and orientation, not the order in which rings begin. Avoid random delays large enough to make outer plants appear before inner ones.

| Time relative to a plant's reveal | Proposed pose |
|---|---|
| 0 ticks | Compressed just below the surface |
| 4 ticks | Fully emerged; vertical scale approximately 1.10 of the final pose |
| 6 ticks | Slight compression, approximately 0.97 |
| 8 ticks | Settle to 1.00 |

This is a sprouting bounce: after emergence, keep the foot on the ground while the plant's upper silhouette stretches and settles. Do not make the whole plant jump or slide over the surface. Both tall-grass halves use identical animation progress and a common foot pivot. Preserve the confirmed center-outward ground synchronization throughout.

Vanilla display interpolation handles translation and scale between transform targets, with quaternion rotation interpolation. A named easing curve such as smoothstep is not automatically available through an NBT option. Implement the confirmed bouncy feel using a short sequence of interpolated transform targets. The proposed overshoot/compression poses above specify those targets; confirm their timing and amplitude before adding them.

Server tick alignment is not a guarantee that two different packets become visible on the exact same client frame. The center's tracking lead is also a real exception. Multiplayer review must check perceived alignment before claiming exact synchronization.

### Idle movement

Suggested motion: a slow 1–3 degree tilt around the plant's foot, with different phases per patch and a little variation per plant. Keep scale stable after growth. Avoid repeated vertical pumping or synchronized motion across the entire field.

For block displays, use occasional interpolated transform targets rather than sending a transform every rendered frame. A starting keyframe interval of 18–30 server ticks is a proposal to measure, not a final performance guarantee.

Both tall-grass halves must share the same foot pivot, rotation, growth progress, and timing. Independently tilting the upper half would split the plant at its seam.

### Withdrawal

Preserve the current tick-104 retraction start and tick-120 removal. Lower and shrink each plant into the ground over 10–14 ticks. Small patch-based offsets are acceptable only if every display finishes before removal. Use translation and scale for disappearance; an opacity fade is not part of the vanilla block-display contract.

Cancellation, chunk unload, and shutdown must still discard displays immediately. Cosmetic withdrawal must not delay gameplay cleanup.

## 5. Renderer and NeoForge implementation contract

### Recommended backend: retain vanilla block displays

Keep server-side placement and lifecycle in `DryadRootSnareManager`, and transformation serialization in `SnareVegetationDisplay`.

The server creates entities with `EntityType.BLOCK_DISPLAY`. The client receives vanilla block-display entities; it does not automatically receive custom Java fields from the server helper subclass. No new entity registry or custom entity-renderer registration is needed for this route.

In the project's 1.21.1 mapped sources, the relevant transformation and block-state setters are private. The existing subclass therefore uses the protected `readAdditionalSaveData` path with `block_state`, `transformation`, `interpolation_duration`, and `start_interpolation`. Keep that adaptation isolated in the display helper. Do not invent public setters or reload the whole entity to change its transform.

For a native plant-model vertex `v`, the desired conceptual transform for half `i` is:

```text
worldVertex = T(groundBase) * R(yaw) * R(footTilt)
              * S(horizontalScale, verticalScale * growth, horizontalScale)
              * T(-0.5, i, -0.5) * v

i = 0 for a single plant or the lower half; i = 1 for the upper half.
```

This centers the model and tilts both halves around the same foot. When serializing display `translation`, `left_rotation`, `scale`, and `right_rotation`, derive each half's translation from this common transform. Merely adding a vertical offset works for upright growth but is insufficient once the plant tilts.

Suggested planning data, created once and owned by the snare:

| Record | Required information |
|---|---|
| Plant patch | Stable ID, center, coverage shape, species bias, wind phase |
| Plant placement | Ground `BlockPos`, sub-block offset, species `BlockState`, scale, yaw, patch ID |
| Animation state | Spawn tick, reveal tick, growth phase, next idle keyframe, withdrawal state, display references |

Keep client rendering classes under the client package. The common/server managers must not call `Minecraft.getInstance()`, `RenderSystem`, or other client-only classes. NeoForge's [sides documentation](https://docs.neoforged.net/docs/1.21.1/concepts/sides/) explains the physical/logical side distinction.

### Alternative backend, only if explicitly selected

A client-only vegetation renderer could draw the same `BakedModel`s using `PoseStack`, `MultiBufferSource`, `BlockRenderDispatcher`, packed world light, and `OverlayTexture.NO_OVERLAY`. It would allow frame-based foot sway and custom tint control while avoiding one server entity per plant half, but adds synchronization and rendering work.

If chosen, follow the project's client-only `RenderLevelStageEvent` integration and verify the chosen stage and render type against the 1.21.1 sources. Reuse model-provided render types and NeoForge's entity render-type conversion for baked models; do not render plant models using the additive mote shader. Account for the camera-relative transform exactly once, restore render state, and never flush buffers per plant unnecessarily.

This backend needs its own field payload; the existing `RootSnareVfxPayload` only identifies a captured entity and its binding dimensions. A proposed field payload would carry a snare ID, dimension, server time, bounded placements or seed plus approved surfaces, and ring/cancellation updates. Late trackers need current state rather than replaying the whole growth animation.

Register any new `CustomPacketPayload` and `StreamCodec` through the existing `NetworkHandler` and `RegisterPayloadHandlersEvent` on the mod bus, using the 1.21.1 `playToClient` convention. Client state changes must execute on the game thread. Follow the versioned [payload documentation](https://docs.neoforged.net/docs/1.21.1/networking/payload/), not newer-version registration examples.

This alternative is not required by the recommended woodland-patch appearance.

## 6. Performance and world-state checks to agree on

At the current density and species mix, an unobstructed 69-tile field averages about 57 plant groups and 88 display entities, before target accents. These are approximate planning counts, not measured server or GPU timings. More plants per tile would increase those costs quickly.

Proposed first-pass budget: one group per eligible tile, at most 69 field groups / 138 field displays, with a separate confirmed limit for dead-bush accents. Measure multiple simultaneous snares before increasing the budget or adding frequent idle keyframes.

Every display must belong to one snare lifecycle and remain nonpersistent. Retain failure cleanup when one half cannot spawn. Handle changed terrain between staging and reveal, repeated casts, nearby snares, removed targets, and tracked chunks unloading.

Temporary removal of real terrain cover remains separate from decorative displays. Restore native double-height plants bottom-half first, preserve original blocks, and avoid replacing player-built obstructions just to fill a visual gap.

## 7. Step-by-step confirmation before coding

The following was the confirmation sequence before coding. The user subsequently authorized the proposed first pass and explicitly selected normal world lighting. The record below distinguishes direct user choices from implementation tuning selected under that authorization.

1. **Record the selected visual direction.** A woodland patches is confirmed; keep its varied heights and orientation.
2. **Confirm palette and density.** Tall grass and fern only in the field; dead bushes near bindings; no flowers or petals. Confirm roughly 80% coverage, patch grouping, height ranges, and one group per tile.
3. **Confirm readability rules.** Decide the space left around targets, how nearby accents merge, and how visible plants withdraw when a target occupies their position.
4. **Tune the confirmed animation direction.** Bouncy emergence and synchronization with moss/rooted dirt are confirmed. Approve duration, overshoot/compression amplitudes, shallow underground start, idle sway, and withdrawal. Decide whether the center's tracking lead is acceptable.
5. **Confirm the backend.** Retain vanilla block displays, or separately approve a client-only baked-model renderer. Record whether custom biome tint or per-plant color is required.
6. **Confirm lifecycle and budgets.** Agree on field/accent caps, overlapping-snares behavior, terrain handling, cancellation, and late tracking.
7. **Confirm the review criteria.** Define what must be seen in the eventual in-game review: readable outward growth, intact tall-grass seams, visible roots, restrained idle motion, correct cleanup, and acceptable multiplayer performance.
8. **Approve the concrete implementation scope.** Only then edit code. A client launch or JAR build still needs its own instruction.

### Decision record

| Decision | Original proposal | Selected implementation / authorization |
|---|---|---|
| Overall vegetation direction | Woodland patches | Direct user choice; implemented |
| Field palette | Tall grass + fern | Dominant grass/ferns and short grass at edges; sparse tulips, daisies, dandelions, rose bushes, peonies and lilacs added by user request |
| Target accents | Small dead bushes | Retain the model's four brown item pieces; no additional target bushes |
| Density/layout | About 80%, irregular patches, one group per tile | Seven overlapping patch influences; one group per eligible cell; 82.9% mean sampled coverage |
| Growth | Center outward; staged before reveal; 8-tick bounce | Ground synchronization and bounce are direct user choices; 4-tick rise, 2-tick compression, 2-tick settle selected for the first pass |
| Center tracking lead | Retain 2 ticks initially | Retained; outer rings are staged before their transform |
| Idle | Gentle foot tilt; occasional interpolated keyframes | Vegetation tilt up to 0.04 radians, with 22-tick interpolation and patch phases |
| End | Retract within current final 16 ticks | Superseded by user: foliage stays grown until direct disposal; binding withdrawal is retained |
| Renderer | Vanilla block displays | Native block displays for the field; native mixed block/item displays for the binding |
| Color behavior | Existing vanilla block colors/textures | Existing native models/textures; normal sampled world lighting, explicitly requested by user |
| Budgets and edge cases | Limits above, then measure | Maximum 69 field groups / 138 field displays, plus 11 parts per distinct captured target; overlapping owners share a binding |
| Binding model | Replace procedural root emergence with supplied assembly | Implemented with all 11 parts and eight changing poses; omitted updates retain prior poses |
| Binding size floor | Adult-pig reference; grow with target hitbox | 0.9-block reference floor; separate horizontal/vertical fit, with a 14% vertical increase; opening still requires in-game review |
| Permission to implement | Wait for agreed scope | User authorized code on 2026-10-08; JAR build and client launch remain separately controlled |

## 8. Implementation order after confirmation

1. Add the agreed stable patch layout and placement mask; review placement without changing root or particle code.
2. Apply the agreed reveal schedule and bouncy, ground-anchored growth targets. Keep both grass halves joined.
3. Add the approved idle keyframes and withdrawal behavior using the same shared pivot.
4. Integrate target clearance and retain the model's brown item pieces, including graceful removal of visible obstructing foliage.
5. Convert the supplied binding part/pose data using the agreed native-display or client-rendered backend. Replace the old procedural root geometry while preserving separately agreed mote effects. Verify lifecycle cleanup, terrain restoration, and compatibility with the selected backend.
6. Compile Java without building a mod JAR. When explicitly instructed to run the client, review flat and uneven terrain, low ceilings, native tall plants, small/large targets, crowded captures, repeated casts, overlapping snares, chunk boundaries, resource packs, and multiplayer tracking.
7. Record observed issues and tuning decisions here before expanding the overhaul to root models, motes, ground visuals, or audio.

## Source notes

Current-behavior facts and private-method details were checked against this repository and its generated 1.21.1 mapped sources. Relevant project files are `DryadRootSnareManager.java`, `SnareVegetationDisplay.java`, `RootSnareVfxPayload.java`, `NetworkHandler.java`, and the `BeamRenderer` render-stage integration. Proposed values and architectural alternatives are design recommendations, not existing behavior or measured results.

## 9. Supplied animated binding model — inspected 2026-10-08

### User requirements recorded

- Replace the current procedural root-emergence render with the user's model and spawn animation.
- A faithful visual adaptation is sufficient; exact reproduction is not required.
- Scale the binding to the snared entity's hitbox, with an approximate adult-pig-sized minimum.
- Analyze and discuss first. The initial code pause was later superseded by explicit implementation authorization; use normal world lighting.

The `.mcfunction` commands are source data to interpret. They are not instructions to execute on the user's world, and their exported selectors/schedules are not the mod's lifecycle design.

### What the files contain

Source folder: `C:/Users/LENOVO/Downloads/vine`. Inspected `create.mcfunction` and every file from `keyframe_0.mcfunction` through `keyframe_14.mcfunction`.

The create function contains two untextured block-display containers and **11 visible model parts**:

| Exported part IDs | Display type | Material | Role in the authored assembly |
|---|---|---|---|
| `project_0`, `project_1` | `block_display` | `minecraft:cave_vines`, berries=true | Green vine planes with glow-berry texture details |
| `project_2`, `project_3` | `block_display` | `minecraft:cave_vines`, berries=false | Additional green vine planes |
| `project_4`, `project_5` | `block_display` | `minecraft:sweet_berry_bush`, age=0 | Overlapping young-bush silhouettes |
| `project_6` | `block_display` | `minecraft:sweet_berry_bush`, age=1 | Additional smaller bush silhouette |
| `project_7`–`project_10` | `item_display` | `minecraft:dead_bush`, item_display=none | Four angled brown item-model pieces around the binding |

The assembly therefore mixes baked **block models** and baked **item models**. It is not solely an item-display layout, nor a conventional mesh model with bones. The brown pieces must retain the item-model path and `ItemDisplayContext.NONE`; replacing them with dead-bush block models would change their appearance and pivot.

The two container displays organize the BDEngine export. An adaptation can use one shared model origin and direct ownership of the 11 parts without reproducing those empty container entities.

### Matrix interpretation

Every authored transform is a 16-float affine matrix, written row by row. In this Minecraft version, `ExtraCodecs.MATRIX4F` reads it using `setRowColumn(row, column, value)`. Translation is stored at list positions 3, 7, and 11; the last row is `[0, 0, 0, 1]`.

Preserve the complete matrix for each part. These transforms combine orientation, nonuniform scaling, and translation; they cannot be reduced safely to a single yaw and height. Do not pass the row-major list directly to a column-major JOML array constructor.

Native display rendering can decode the authored matrices into Minecraft `Transformation` values. A client-rendered alternative must apply the same model transform through `PoseStack` and the appropriate block/item renderer. Neither route requires running the exported commands.

### Actual spawn motion and timing

| Exported frame(s) | Contents |
|---|---|
| 0 | All 11 parts receive the initial compressed pose, matching the create layout. |
| 1 | The 7 block-display parts rise/stretch; the 4 brown item pieces retain their previous pose. |
| 2 | All 11 parts update, bringing in the brown pieces. |
| 3–4 | The assembly overshoots its settled height. |
| 5–6 | It contracts and settles. |
| 7 | The 4 vine planes and 4 brown pieces reach their final pose; the 3 berry-bush pieces keep frame 6. |
| 8–14 | No transformation updates. These are hold/scheduling steps. |

Each moving frame specifies `interpolation_duration:2` and `start_interpolation:0`. The functions schedule their pause checks after `0.1s`, corresponding to 2 ticks at the normal 20-tick-per-second simulation rate.

As a concrete example, `project_4`'s Y scale is about 0.422 initially, peaks at 1.898 in frame 4, then settles at 1.688. This confirms the authored overshoot-and-settle character. Horizontal orientation is mostly established in the initial model; the spawn primarily changes vertical scale and position rather than tracing a new twisting path.

**Selected interpretation:** Play the authored changing poses 0–7 at 2-tick intervals, giving about 14 ticks / 0.7 seconds to reach the last target pose, with native interpolation completing its final segment afterward. Hold the final pose while active, with restrained group tilt. Retain the small delayed arrival of the brown pieces. The unavailable pause/loop helpers are not reproduced.

The referenced `project:k/spawn/check_pause_0` through `check_pause_13`, plus `check_loop`, are not among the supplied files. Their pause/repeat behavior and extra scheduling overhead cannot be reconstructed exactly. They are unnecessary for a self-contained mod adaptation of the supplied pose data. The implemented quiet group tilt and underground withdrawal are mod additions, not animations present in these files.

### Hitbox fitting and the pig-sized floor

The project's mapped `EntityType.PIG` defines adult dimensions of **0.9 blocks wide and 0.9 blocks tall**. Use this as the reference minimum, rather than allowing small mobs' bindings to shrink to almost nothing.

Proposed fitting procedure:

1. Inspect the visible settled silhouette and its central opening, using the real block/item models and their pivots. Do not treat the raw matrix translations as the binding's outer size.
2. Center the assembly on the target's X/Z hitbox center. Anchor it to the sampled ground surface/original unsunk feet position; the existing gameplay offset moves the mob down 0.3 blocks and must not pull the entire model underground.
3. Define one calibrated baseline fit for an adult pig. This includes a small clearance around the hitbox and the desired visible binding height.
4. Calculate effective horizontal target dimensions as `max(actual hitbox width/depth, 0.9)`. Increase the group scale for larger targets; small targets retain the pig baseline.
5. Fit vertical height separately. Use the pig baseline as a minimum, then increase it moderately for taller targets while keeping the upper body readable. Do not multiply the whole assembly uniformly by a tall mob's full height ratio.
6. Apply the same group fit to every part and every keyframe, including translations. This preserves the relative layout and overshoot. Per-part scale clamping would distort the animation.

Conceptually:

```text
partWorldMatrix(t) = T(ground-centered anchor) * R(stable assembly yaw)
                     * S(group hitbox fit) * T(authored centering correction)
                     * authoredPartMatrix(t)
```

Prefer uniform horizontal scaling using the larger width/depth requirement unless fitting an elliptical footprint is explicitly desired. The existing binding payload carries width and height only; separate width/depth fitting would need an added depth value or a reliable client-side bounding-box read.

The final block-part envelope obtained by transforming entire unit cubes is roughly X -0.99..0.99, Y -0.41..1.41, and Z -0.87..1.03. **This is only a conservative envelope**, not the visible silhouette or the inner opening: plant models use crossed planes, and the item pieces have different model pivots. Consequently, simply shrinking the envelope to a 0.9-block square would not establish a correct pig fit. Calibrate the opening, not just the outer bounds.

The selected clearance, vertical cap, and baseline normalization are recorded in section 11. Their transforms have been checked mathematically; the visible opening still needs in-game review.

### Proposed adaptation into the existing mod

The closest match is a **mixed native display assembly**: seven `Display.BlockDisplay` parts plus four `Display.ItemDisplay` parts per captured target. Vanilla renderers then supply the original block and item visuals. Keep typed part/pose data and direct display references, owned by the snare lifecycle. No generic `project_*` selectors or command schedules should be used at runtime.

The exported item stack uses `Count:1`. The implementation creates a normal `ItemStack` through the 1.21.1 API rather than copying legacy stack SNBT blindly. The export's sky/block brightness overrides are omitted as the user requested normal world lighting. The native display renderer samples light at each display entity's position; this does not turn the model's berry details into actual light sources.

A client-only mixed block/item renderer is an alternative if display-entity counts become expensive. It would reuse `BlockRenderDispatcher` and `ItemRenderer`, apply the authored matrices in `PoseStack`, and use an absolute animation clock with partial ticks. That route needs an explicit choice because its ownership, tracking, lighting, and animation interpolation differ from native displays.

Whichever route is selected:

- Replace the old bark-cuboid emergence, branches, moss strips, and geometric binding-pulse shell rather than drawing both assemblies simultaneously.
- Treat green/white motes as a separate existing effect; do not remove or intensify them without a new decision.
- Adapt burrowing particles to the new assembly's ground contacts instead of relying on the removed root-shape list.
- Avoid adding extra dead-bush clutter automatically: the supplied model already has four brown dead-bush pieces.
- Hold frame 7 after spawn. Gentle idle motion and final retraction may be proposed next, but are not present in the supplied clip.
- Store one per-target animation start and fit; preserve all part relationships through movement, interruption, overlapping snares, and cleanup.

### Selected decisions for this model

| Item | Current status |
|---|---|
| Use the supplied model to replace procedural root emergence | Confirmed design request |
| Approximate the authored animation rather than reproduce perfectly | Confirmed |
| Scale with hitbox and retain a pig-sized minimum | Implemented with independent width and lower-body-height fitting; in-game opening review outstanding |
| Preserve all 11 parts/materials | Implemented; empty export container displays omitted |
| Active spawn duration | Frame targets 0–7 at two-tick intervals; last target at tick 14 |
| Native block/item displays vs client renderer | Native mixed display assembly implemented |
| Export brightness vs normal world lighting | Normal world lighting explicitly requested and implemented |
| Extra target-local dead bushes | Omitted; four authored brown item pieces retained |
| Idle and end animation | Subtle whole-group tilt and a 14-tick underground withdrawal |
| Permission to code | Authorized by user on 2026-10-08 |

## 10. Optional lushness and vibrancy refinements

**Status:** Suggestions originally requested by the user, followed by authorization to implement the first pass. Height layers, short grass at edges, fuller fern grouping, the shared breeze, and the existing model's berry details were selected. Large ferns, custom texture/tint variants, changes to authored brown-piece proportions, and brightness pulses were not included.

### Recommended composition

Use three layers: low greenery around patch edges, a grass-and-fern middle layer, and a few taller fern silhouettes. Place the tallest plants away from the binding opening. A varied silhouette and overlapping neighboring patches should supply most of the lushness; increasing display count alone is not the first step.

| Suggestion | Visual result | Minecraft/NeoForge implementation implication |
|---|---|---|
| **Layer the heights** | Low growth connects the moss floor to fuller fern clumps and taller grass. | Optionally replace a small share of tall-grass placements with `Blocks.SHORT_GRASS`. Keep existing scales varied within stable ranges. Species changes use existing baked block models. |
| **Make fern groups fuller** | Small groups of ferns form broad green masses, with grass filling between them. | Give neighboring tiles a shared fern bias instead of selecting each independently. Optionally replace a few grass groups with scaled `Blocks.LARGE_FERN`; its lower and upper halves need the same paired-display/pivot handling as tall grass. Replacement keeps the display budget bounded. |
| **Soften patch edges** | Dense interiors transition into shorter, scattered greenery instead of ending at a hard circular boundary. | Use patch-local placement weights and height ranges. Retain the same ground eligibility and overall plant budget; do not plant outside transformed terrain. |
| **Connect the binding to the field** | A small fern or low grass group frames the outer side of the binding, while the central opening stays readable. | Place framing vegetation outside the model's fitted footprint. Retain the clearance mask and merge accents for nearby targets. Do not fill the captured entity's feet with extra plant planes. |
| **Use the existing warm berry details** | Yellow/orange berries interrupt the green-and-brown palette and draw attention to the binding. | The supplied model already has two `cave_vines` parts with `berries=true`. Preserve them as small warm accents; adding berries to every vine would make the detail less selective. |
| **Balance green and brown silhouettes** | The model reads as living vines supported by woody roots rather than a pile of dry branches. | Keep the authored brown item pieces, but review their horizontal width and exposed area against the vine planes. Any reduction or repositioning is a deliberate model adaptation, not a switch to a different item/block model. |
| **Add one restrained bounce and a shared breeze** | Plants feel alive without constantly hopping. | Start growth with the ground change. Vary overshoot amplitude slightly per patch, while retaining the common reveal schedule. Idle foot-pivot tilts can share a slow wind direction with different phases; both tall-plant halves remain joined. |
| **Give light accents a purpose** | Sparse green/white motes lift around the binding, and the berries remain a warm focal detail. | Retain the existing mote effect as the baseline. A brief brightness pulse on selected berry parts at tightening is optional; avoid adding another continuous layer of particles simply to create density. Native display brightness changes the rendered light level, not actual world illumination. |

### Suggested palette and proportions

Start with the existing grass and fern textures. Let fern clusters provide fuller leaf shapes, grass provide finer vertical detail, the moss ground provide the low green base, and the supplied dead-bush item pieces provide brown structure. Keep the warm berry accents small.

If a third vegetation species is desired, a provisional field mixture is about 50% tall grass, 35% fern, and 15% short grass. This is an artistic starting point, not an approved quota. A few large ferns could replace tall-grass groups rather than being added on top of the existing placements.

For a stronger palette, darker fern centers and lighter green tips would be attractive, but this is a separate optional asset/renderer choice. Vanilla block displays do not expose a general per-part RGB tint. Do not promise custom color variation through ordinary display NBT. Existing textures and silhouettes are enough for the first pass; custom texture variants or a client renderer would need explicit selection.

### Recommended first pass to confirm

1. Keep dense woodland patches and add a clearer low-to-high silhouette, initially through scale variation and fern grouping.
2. Preserve the supplied warm berry details and brown item pieces; judge their balance once the model is fitted to an adult pig.
3. Use a short, modest emergence bounce and a quiet breeze with patch-based phases.
4. Maintain clear space around the binding and a few gaps between patches so both the terrain wave and model remain readable.
5. Keep particle volume restrained. Review model/plant silhouette before approving extra light or particle accents.

Short grass was included as the low edge layer. Large ferns, altered brown-piece proportions, custom greens, and brightness pulses remain optional future changes. Normal world lighting is the implemented lighting choice.

## 11. Initial first-pass record — 2026-10-08

This records the initial implementation and validation. Section 12 supersedes its foliage withdrawal, palette, offsets, height layering, binding vertical multiplier and mote settings. Shared ownership, native renderer paths, normal world lighting, terrain synchronization and binding withdrawal remain in use.

### Ownership and renderer paths

| File | Responsibility |
|---|---|
| `gene/dryad/server/DryadRootSnareManager.java` | Preserve the existing ground/capture schedule, stage and reveal foliage, clear foliage around bindings, tick display groups, restore terrain and dispose of visuals. |
| `gene/dryad/server/SnareVegetationLayout.java` | Generate one stable, seeded woodland layout with patch-specific species bias, height and wind phase. |
| `gene/dryad/server/SnareVegetationDisplay.java` | Server helper subclass of native `Display.BlockDisplay`; apply the paired plant transform and block state. |
| `gene/dryad/server/SnareDisplayTransforms.java` | Encode row-major matrices and native interpolation/culling parameters through the protected display NBT reader. |
| `gene/dryad/visual/RootSnareModel.java` | Store all eight resolved poses for all 11 parts, and calculate common hitbox fitting and withdrawal transforms. Uses JOML; contains no client renderer imports. |
| `gene/dryad/server/SnareBindingInstance.java` | Own seven native block displays and four native item displays per target; animate, follow X/Z, share between overlapping owners, and discard the assembly. |
| `gene/dryad/client/RootSnareRenderer.java` | Retain the green/white rising motes and short ground-texture debris effect. The previous procedural bark geometry is removed from this renderer. |

All Java paths above are relative to `src/main/java/net/sonflowers/jeecore/`. The original `.mcfunction` files remain reference data and are neither modified nor executed. No replacement texture files, custom entity types, custom display renderers, or new network payloads are needed. Vanilla tracking sends the display state to clients, which render through `DisplayRenderer.BlockDisplayRenderer` and `DisplayRenderer.ItemDisplayRenderer`.

The existing `RootSnareVfxPayload` remains responsible for motes/debris. A zero-duration update cancels those effects when a snare ends and no other binding owner remains. Native displays provide the binding and foliage independently of that payload.

### Field placement and growth

- One central patch influence and six offset patch influences produce neighboring fern clusters rather than independent species choices. Overlapping influences still produce at most one plant group per eligible ground cell.
- Patch fern bias ranges from 0.28 to 0.70. The short-grass probability rises from 0.08 inside a patch to 0.21 at its edge. Height bias is 0.92–1.08, with a small additional reduction at edges.
- Initial species scales are tall grass 0.53–0.71, fern 0.72–0.99, and short grass 0.45–0.68 before patch/edge height adjustments. Tall grass uses two joined native block models; fern and short grass use one each.
- Plant offsets vary by up to 0.17 blocks from each cell center. Yaw, species, scale and phase remain stable for the life of the snare. The field has a common wind direction, with different motion phases for its patches.
- The center is prepared at activation and retains a two-tick tracking lead. Ring 1 is also prepared at activation; each later ring is prepared when the preceding ring transforms. Outer growth begins at ticks 3, 6, 9 and 12, after its moss/rooted-dirt update in the same server tick.
- Before reveal, the manager checks that this snare actually transformed the sampled ground and that the required plant spaces are now air. An obstruction or changed surface discards the staged group rather than forcing vegetation through it.

The spawn target sequence is:

| Plant age | Transform target | Interpolation duration |
|---|---|---:|
| Before reveal | Growth 0.02; sunk approximately 0.157 blocks | Initial state |
| 0 | Rise to a stable per-plant overshoot of 1.075–1.11 | 4 ticks |
| 4 | Compress to 0.97 | 2 ticks |
| 6 | Settle to 1.0 | 2 ticks |
| 8 onward | Maintain size; gentle foot-pivot tilt | 22 ticks per idle update |

The matrix for half `i` is `T(sink) * R(footTilt) * S(scale, scale * growth, scale) * T(-0.5, i, -0.5)`. Both halves share the same display origin, yaw, tilt, growth and timing. This makes their common seam remain joined throughout interpolation. The idle amplitude is 0.04 radians for grass/ferns and 0.025 radians for short grass.

Visible foliage within `max(0.9, targetWidth) * 0.6 + 0.25` blocks of a newly captured target retracts over four ticks. Still-hidden groups in that opening are discarded immediately. There are no additional target-local dead bushes; the authored binding supplies those brown details.

Normal field withdrawal begins at tick 104. Plants return to growth 0.02 over their stored 10–14 tick duration and are discarded one tick later, before the tick-120 final cleanup. Cancellation and unload use immediate disposal.

### Binding poses, size and animation

`RootSnareModel` stores 1,408 authored float values: eight resolved poses × 11 parts × 16 matrix components. Parts omitted from an exported keyframe carry their previous pose forward. Hold-only keyframes 8–14 do not create redundant animation updates.

All parts receive the same group transform before their individual authored matrix:

```text
horizontal = 0.78 * max(0.9, targetWidth) / 0.9
vertical   = max(0.9, min(1.22, targetHeight * 0.58)) / 1.4

group = T(0, -0.3 * (1 - growth), 0)
        * Rx(idleTiltX) * Rz(idleTiltZ)
        * S(horizontal, vertical * growth, horizontal)
        * T(0, 0, -0.06)

partWorld = T(originalFeet + 0.01Y) * R(stableYaw) * group * authoredPartPose
```

The 0.9-block reference is the adult-pig floor. Small targets retain the same binding fit as that reference. Larger targets expand the footprint, while taller targets receive moderate lower-body height growth, capped independently so the upper body stays visible. The numeric fit is a first-pass calibration; an actual pig and larger mobs still need to be reviewed in-game to judge the opening against their models.

The ground anchor is captured before the existing gameplay code lowers the mob by 0.3 blocks. It follows target X/Z without copying that lowering into the assembly's Y coordinate. Yaw is selected once from the target's orientation with up to 20 degrees of variation.

Frame 0 is the initial compressed pose. Frames 1–7 become targets every two ticks, retaining the delayed brown-piece arrival and authored overshoot/settle. After growth, the whole group receives quiet X/Z tilts with amplitudes 0.012/0.01 radians and 20-tick interpolation. The group starts a 14-tick withdrawal 16 ticks before its current end, scaling its height to 0.02 and translating below ground. A later overlapping capture extends the same group's end and restores its settled size if withdrawal had started.

### Normal world lighting

The animation tags omit `brightness` entirely. Native displays sample normal packed light at their entity positions for both foliage and binding parts; the exported sky-light 15/block-light 3–5 override is not copied. There is no brightness pulse, full-bright binding pass, cylindrical glow, or world-light emission added by these display models.

The existing additive green/white motes remain a separate particle-like accent. The model's vine, berry, bush and brown-item textures are rendered through their normal native model paths and respond to ordinary world lighting. Custom biome-position tinting is not added by this pass.

### Lifecycle and budget

- Field placement is capped structurally at 69 groups / 138 native field displays by the existing ring cell set and one-group-per-cell rule.
- A binding adds 11 visible display entities per distinct captured target. Empty export containers are omitted. This is a structural count, not a measured performance guarantee or a new gameplay target cap.
- Concurrent snares capturing the same target share one binding assembly. A same-tick guard prevents duplicate animation updates, and the last owner discards it.
- Failure to spawn any part discards already-created siblings. Losing one display or the target disposes of the remaining group.
- Display helpers are nonpersistent. Expiry, repeated casts, chunk unload, level unload and server stop use the manager's cleanup path. Chunk checks include scaled binding bounds and field plant locations.
- Original terrain/cover restoration remains separate from visual disposal. The existing capture/NoAI/effect behavior is preserved; this implementation does not resolve the previously diagnosed gameplay overlap/AI-reset issue.

### Validation performed

`compileJava` passed using the project's existing offline Gradle cache. Only Minecraft dependency preparation and Java compilation ran; no mod JAR was built and no development client was launched.

Headless checks exercised the actual Minecraft `Transformation.EXTENDED_CODEC` and native matrix decomposition, rather than a substitute renderer:

| Check | Result |
|---|---|
| Imported pose values compared with the supplied files | All 1,408 values match |
| Native codec/decomposition across 5 widths, 4 heights, 8 frames and 11 parts | All 1,760 combinations finite and nondegenerate; maximum matrix-component difference 0.003839 blocks |
| Tall-grass seam/foot tests across scale, growth, tilt and wind direction | 180 cases passed; maximum seam difference 0.00000060 blocks |
| Minimum fit and withdrawal | Small targets use the pig-reference fit; larger footprints increase; all withdrawn model bounds are below ground |
| Staged plants and lighting tags | Staged foliage is below the surface; animation tags contain no brightness override |
| Stable layout sampling | 500 seeds passed determinism and scale checks; center always attempts placement |
| Sampled density on 69 unobstructed eligible cells | Mean coverage 82.9%; 57.2 plant groups / 81.6 field displays before capture clearance |

The small matrix difference comes from native transformation decomposition; the same native path is used by the supplied display commands. These checks validate transforms and layout, not visual quality, packet arrival timing, server/GPU cost, or resource-pack compatibility.

### Next review, when a client launch is requested

1. Review normal daylight, shade, indoors and night lighting on both native display types.
2. Compare a pig, a smaller target and a larger/taller target; tune the opening or lower-body height only if their visible models require it.
3. Watch center/outer-ring tracking and the bounce at normal speed, including multiplayer and late tracking. Confirm plants visibly follow transformed ground and tall grass has no seam.
4. Review uneven terrain, low ceilings, native double-height cover and resource-pack plant/item models.
5. Exercise crowded captures, overlapping snares, repeated casts, removed targets and chunk boundaries. Confirm display disposal and terrain restoration in-game.
6. Measure multiple simultaneous fields before increasing density or animation update frequency.

## 12. Current refinements — 2026-10-08

### Foliage removal and binding height

Field plants keep their settled size and breeze until tick-120 cleanup, then their displays are discarded directly. Plants inside a captured target's clearance opening are also discarded directly. The manager no longer stores foliage retraction state or sends a withdrawal transform. Bindings retain the 14-tick underground withdrawal beginning 16 ticks before their end; cancellation and unload still discard both types immediately.

`RootSnareModel.verticalScale` multiplies the previous lower-body fit by **1.14**:

```text
vertical = 1.14 * max(0.9, min(1.22, targetHeight * 0.58)) / 1.4
```

The footprint, pig-reference minimum, grounded origin, part relationships, authored poses and idle tilt are retained. The increase affects height only.

### Flower variety, radial layers and off-center positions

`SnareVegetationLayout` receives `MAX_RADIUS + 0.5` from the manager. The seven stable patch influences still group ferns, but height/species layering now also considers distance from the whole field center:

```text
radialEdge = clamp(distanceFromFieldCenter / (MAX_RADIUS + 0.5), 0, 1)
tallFlowerChance = 0.075 - radialEdge * 0.055
shortFlowerChance = 0.10
shortGrassChanceWithinRemainingGreenery = 0.06 + radialEdge * 0.22
heightMultiplier = patchHeightBias * (1.10 - radialEdge * 0.30)
                   * (1 - patchEdge * 0.05)
```

This makes the center generally taller, with shorter scales, more short grass and fewer tall flowers near the outside. It remains a natural variation rather than a rigid height staircase. Grass and ferns dominate the remaining selections.

| Species | Native block model | Starting scale before height adjustments |
|---|---|---|
| Tall grass | `Blocks.TALL_GRASS` | 0.53–0.71 |
| Fern | `Blocks.FERN` | 0.72–0.99 |
| Short grass | `Blocks.SHORT_GRASS` | 0.45–0.68 |
| Short flowers, equally selected | `Blocks.WHITE_TULIP`, `Blocks.OXEYE_DAISY`, `Blocks.DANDELION` | 0.62–0.82 |
| Tall flowers, equally selected | `Blocks.ROSE_BUSH`, `Blocks.PEONY`, `Blocks.LILAC` | 0.58–0.78 |

Tall flowers use the existing `DoublePlantBlock` lower/upper pairing, shared foot pivot and synchronized bounce/sway. Both halves must have air space. All flowers wait for their matching ground ring's moss/rooted-dirt transformation, just like the greenery.

Each placement now has a stable displacement of **0.12–0.30 blocks** from its cell center in a random horizontal direction. Perfectly centered placements are excluded. Its ground cell still supplies surface/obstruction checks, and species, position, scale and orientation do not reroll during animation.

### Simpler yellow/white motes and leaf dust pillars

`RootSnareRenderer` draws each mote as two camera-facing quads: a yellow square (`255, 222, 108`) and a smaller white center. The two glow halos are removed, and ordinary alpha blending replaces additive blending. The eight-mote count and size range are retained.

Individual mote lifetimes increase from 65–89 ticks to **90–119 ticks**, with a slower rise speed of **0.0045–0.0065 blocks/tick**. Lifetime and end-of-snare fades remain; cancellation still removes the effect immediately.

During binding emergence, the four staggered ground contacts each emit a single three-particle burst of `BlockParticleOption(ParticleTypes.DUST_PILLAR, Blocks.OAK_LEAVES.defaultBlockState())`: at most **12 leaf dust pillars per emergence**. A per-contact bit mask prevents duplicate bursts while allowing emission on the first eligible rendered tick if the initial frame was missed. The native dust-pillar provider supplies leaf fragments, random pillar motion and 20–39 tick lifetimes. Restrained ground-block debris is retained.

The native binding and vegetation still use normal world lighting. No display brightness override or new texture asset is introduced.

### Validation for this revision

- Java compilation passed with the existing offline Gradle cache. No mod JAR was built and no development client was launched.
- 500 deterministic layouts retained **82.9%** mean eligible-cell coverage. Flowers represented **13.4%** of plants, including **3.7%** tall flowers; grass and ferns represented **86.6%**.
- Mean model height was **0.997 blocks in the inner area** (`distance < 1.5`) versus **0.726 blocks at the outer edge** (`distance > 3.5`). These estimate native plant height multiplied by group scale, not the on-screen silhouette.
- Mean field count was **79.4 displays** before capture clearance. The structural maximum remains 69 groups / 138 field displays. All sampled offsets stayed within 0.12–0.30 blocks and were deterministic.
- **275 interpolated tall-plant seam checks** exercised Minecraft's transformation codec and native component interpolation through emergence, compression, settling and sway. Maximum seam difference was **0.00095987 blocks**, less than 0.016 of a 16×16 texture pixel.
- The raised binding retained its pig-reference floor and withdrew below ground across checked small/pig/large fits. Animation tags still omit brightness overrides.

In-game review remains necessary for perceived flower balance, the taller binding's visibility, leaf-dust intensity, resource-pack models and multiplayer timing. Follow the review sequence in section 11, now expecting direct foliage disposal and yellow/white motes.

## 13. Latest timing and particle revision — 2026-10-08

Implemented at the user's request, without compilation, tests, a JAR build or a client launch:

- Overall snare lifetime increases from 120 to **160 ticks / eight seconds**. Capture still occurs when the outer growth ring completes at tick 12. Binding/mote duration and the existing gameplay effects use the extended lifetime.
- Snare motes share `HealRenderer.drawSpark`: the same two-quad yellow halo and pale white core, with additive blending. Their longer individual lifetimes and slower rise remain from section 12. The heal-ring drawing itself remains the same.
- Vegetation withdraws over **four ticks per group**. Ring 4 begins at tick **124**, followed by rings 3, 2, 1 and 0 at **128, 132, 136 and 140**. Each group is discarded one tick after its interpolation finishes; the center is removed at tick **145**.
- Bindings begin withdrawal afterward, at tick **146**, using a **12-tick** animation and final cleanup at tick **160**. `RootSnareModel.WITHDRAW_TICKS` and `WITHDRAW_LEAD_TICKS` supply the shared binding timing. Overlapping owners continue to extend the shared binding's end.
- Capture emits a small server-tracked burst of **two `ParticleTypes.CLOUD` particles at speed 0.15** around the target's lower body, with spread sized from its width. This replaces the initially requested poof burst. Leaf dust and ground debris during emergence remain.

Normal world lighting, flowers/greenery balance, off-center positions, radial height layers, the 14% binding-height increase and immediate cancellation/unload cleanup remain unchanged. Visible foliage inside a newly captured target's clearance opening is still removed directly; the reverse wave is the normal end-of-snare sequence.

## 14. NoAI diagnosis and repair — 2026-10-10

The supplied server log reports `NoAI` becoming false during active captures of vanilla husks and cows. These warnings read the actual server-side mob flag; they are not a client-rendering problem. They do not contain a caller stack and therefore cannot identify a particular interfering mod. The nearby C2ME item-stack messages do not establish who changed the AI flag.

Code inspection found a definite overlap failure:

1. Snare A captured an ordinary mob and saved its original `NoAI=false`.
2. Snare B captured the same mob and independently saved the now-snared `NoAI=true`.
3. A's cleanup restored false despite B still owning the capture, allowing AI to resume early.
4. B's cleanup could subsequently restore true, leaving AI disabled after all captures ended.

This is a confirmed code path, not proof that overlap caused every warning in the supplied log.

The repair shares one original entity-state snapshot between owners. A later capture does not lower the same mob again. Cleanup releases its ownership first and restores AI, height and the snare effects only after the last owner releases that target. Originally AI-disabled mobs retain that original state.

`MobNoAiMixin` guards `Mob.setNoAi(false)` while a server-side capture is active. The first blocked request per shared capture logs the caller stack, providing evidence for setter-based interference in the next modpack run. The original mob behavior resumes outside active captures. A lowest-priority `EntityTickEvent.Pre` listener and the existing server-tick loop reapply the flag if an entity-data edit bypasses that setter; navigation stops on capture/recovery.

This is an AI-state lock, not a cancellation of the entire entity tick. It does not claim to suppress arbitrary third-party movement code or every off-thread/direct entity-data mutation. Such paths need the new runtime evidence if they continue to interfere.

The Gradle `build` completed successfully; the existing Infernal regression task also passed. The built `build/libs/jeecore-1.0.0.jar` includes the guard class and updated mixin registration. No development client was launched. The new root-snare mixin and ownership behavior still need modpack runtime review; the existing Infernal regression task does not exercise this fix.

