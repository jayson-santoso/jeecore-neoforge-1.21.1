# Terragolem Fuel and Manual Repair System

## Purpose and current scope

Replace ordinary food use and hunger-based natural regeneration for Terragolem with server-authoritative fuel consumption, persistent fuel storage, and manual repair. This document includes the user's September 27 revisions, which supersede the original hunger/saturation repair costs and storage retention on death.

The system provides nine persistent player-owned fuel slots, a disabled future power-core slot, a temporary Fix Fuel slot, a health-tinted clickable body diagram, direct fuel consumption, two custom coal items, and recipes. Power-core behavior and automatic feeding from storage remain out of scope. Holding the beam with powered coal in either hand also attempts normal fuel use while hungry.

## Existing project integration

- Gene checks: `GeneHelper.getPlayerGene(player) == Gene.TERRAGOLEM`.
- Ordinary food/cake restrictions: `TerragolemPassiveHandler`.
- Attachment registration: `GeneDataAttachment`, separate from `PlayerGeneData` ability state.
- Clone handling: `GeneEventHandler.onPlayerClone`.
- Payload registration: `NetworkHandler`.
- Existing alternative/toggle key: `KeyBindings.TOGGLE_ABILITY`, handled by `ClientSetup`.
- Screen registration: `RegisterMenuScreensEvent` in `ClientEventHandler`.
- Authoritative heat: `TerragolemOverheatManager`, with `PlayerGeneData.overheatBar` and the existing heat payload.
- Registry namespace: `JeeCore.MOD_ID`, `jeecore`.

## Locked gameplay rules

### Direct fuel consumption

| Fuel | Vanilla food reference | Hunger added | Saturation points added | Additional effect |
|---|---|---:|---:|---|
| Coal | Carrot | 3 | 1.8 | None |
| Charcoal | Carrot | 3 | 1.8 | None |
| Refined coal | Golden carrot | 6 | 7.2 | None |
| Powered coal | Cooked beef | 8 | 6.4 | Reduce heat by 25% of maximum (125 points) |

Use `Foods.CARROT`, `Foods.GOLDEN_CARROT`, and `Foods.COOKED_BEEF` as the numerical source of truth. Keep vanilla nutrition but multiply their `saturation()` points by `FUEL_SATURATION_MULTIPLIER` (0.5): coal/charcoal 1.8, refined 7.2, powered 6.4. These are already calculated points, not saturation modifiers. This September 28 balance revision supersedes exact vanilla saturation matching. Hunger clamps to 0–20 and saturation to 0–new hunger.

A single tag-driven `TerragolemFuelManager` handles either hand. On the server:

1. Require a living Terragolem and a stack tagged `jeecore:terragolem_fuel` with registered fuel values.
2. Refuse use at full hunger for every fuel, including powered coal, and while the shared 32-tick eating cooldown is active. Check both sides before acknowledging interaction; only the server animates accepted use.
3. Apply the exact nutrition and saturation, clamped as above.
4. For powered coal, call the public heat-manager reduction method with `POWERED_EATING_HEAT_REDUCTION` (25% of maximum heat: 125 points, clamped at zero).
5. Consume one item unless the player has creative inventory privileges.
6. Apply a shared 32-tick (1.6-second) vanilla item cooldown to all four fuels, return a successful interaction result, swing the used hand on the server, and play a consumption sound. Switching hands or fuel types must not bypass the cooldown.

Ordinary food remains blocked, with an explicit fuel path before food-component cancellation. Cake and candle cake remain blocked. Unknown datapack-tagged items may be stored but cannot provide undefined nutrition or repair; no normal-play warning is logged.

### Manual repair — performance minigame

The server requires a living Terragolem, the exact matching open menu, health below maximum, hunger strictly greater than zero, a supported fuel, and the 40-tick (2-second) per-player cooldown.

**Repair does not deduct hunger or saturation.** Zero saturation is allowed. Each successful repair consumes exactly one item, including in creative mode:

| Repair fuel | Base healing (100% quality) | Additional repair effect |
|---|---:|---|
| Coal / charcoal | 2 hearts (4 health points) | None |
| Refined coal | 3 hearts (6 health points) | None |
| Powered coal | 3 hearts (6 health points) | Regeneration I for 100 ticks (5 seconds), reduce heat by 30 points |

Clicking the body starts a three-target GUI repair round instead of healing immediately. Reserve exactly one selected fuel item in a menu-owned server session at the start. Its type and base healing cannot change when inventory slots are rearranged during the round.

Targets appear sequentially in three distinct random body regions. Each target lasts 25 server ticks, giving a full round of 75 ticks (3.75 seconds). The GUI draws a shrinking ring, target marker, Hit/Perfect/Miss text, small sparks, and local click sounds. Click within five pixels of the target center to hit. One attempt is allowed per target; clicks outside the target consume that attempt. A missed click or expired unclicked target immediately ends the round at 50% healing and locks repair for 100 ticks (5 seconds). A hit scores one point; a hit at target age 18 ticks +/- 3 ticks scores two points (Perfect).

For a round completed without a miss, healing equals `base health * (0.5 + score / 6)`, clamped to missing health. Any miss gives 50%, three ordinary hits gives 100%, and three Perfect hits gives 150%. Coal therefore heals 1–3 hearts; refined/powered heal 1.5–4.5 hearts. Display final quality percentage and actual hearts restored. Powered fuel still grants five seconds of Regeneration I and removes 30 heat points on successful completion.

All target generation, attempt limits, timing, score, fuel ownership, and healing are authoritative on the server. Normal container-button requests carry only a target token and body-local click coordinates, bound to the current container ID. No client timestamps, scores, multipliers, or requested healing values are accepted. Standard menu properties synchronize the round; no custom per-tick health payload is added.

Commit the reserved fuel only after successful healing. Closing, disconnecting, dimension travel, losing the gene, reaching full health externally, hunger reaching zero, or cancelled healing aborts the round and returns reserved fuel through storage/inventory/drop fallback. Death places the reserved item into normal death drops exactly once. Completed and aborted rounds start the existing two-second cooldown so closing/reopening cannot rapidly reroll targets. A new menu always starts without an active round. Idle while active ends at 50% quality on the first target timeout and applies the five-second lock.

Direct eating and repair are separate actions. Repair consumes fuel for healing and does not replenish nutrition. Powered coal removes 125 heat on accepted direct eating and 30 heat on successful repair. Full-hunger eating remains blocked.

### Temporary Fix Fuel slot and selection

- A separate, menu-owned slot starts empty on every open and is not serialized.
- Accept only tagged coal, charcoal, refined coal, and powered coal.
- A stack in this slot overrides automatic storage selection.
- If the slot is empty, use storage in tier priority: coal/charcoal, then refined, then powered. Break same-tier ties by storage slot order.
- Reserve one unit per round, consume it on successful completion, and leave the rest of the stack available.
- On closing, return all unused fix fuel to the nine-slot storage. If storage is full, return the remainder to player inventory, then drop any overflow. Never silently delete leftovers.
- Death drops temporary fix fuel together with stored fuel. Clear the slot when taking its contents so later menu closure cannot duplicate them.

Keep balance constants together in the server repair/fuel managers. The cooldown is 40 ticks; powered regeneration is 100 ticks at amplifier 0.

### Ability fuel costs

Initial balancing values are centralized in `TerragolemAbilityFuelManager`:

| Ability action | Food-point equivalent (via exhaustion) |
|---|---:|
| Successful primary burst, 1-2 charged segments | 2 points |
| Successful primary burst, 3-4 charged segments | 3 points |
| Successful primary burst, 5-6 charged segments | 4 points |
| Active right-click held beam | 1 point per 20 held ticks (1 second) |
| Maximum-heat overcharge penalty | 6 points |
| Charge timeout penalty | 4 points |

One hunger point is half a food icon. Costs run only on the server for living, non-creative, non-spectator Terragolem players. Submit costs as vanilla exhaustion (4 exhaustion per food-point equivalent). Vanilla food ticks spend saturation first, then hunger, preserving the fractional exhaustion remainder and vanilla difficulty rules. Never subtract ability costs directly from hunger. Existing ability availability is unchanged at zero hunger.

Left-shot cost is charged only on a valid release, scaling with the three burst charge tiers (2/3/4 food-point equivalents). A shot that triggers overcharge pays the base shot cost plus the overcharge penalty. Overcharge charges only when its existing debuff triggers; timeout charges only once when its charge state expires. Ordinary cancellation and right-button release do not add penalty charges.

Persist partial held-beam usage as `HeldFuelTicks` in the fuel attachment so releasing early, reopening menus, switching genes, or relogging cannot reset fractional usage. Old saves default to zero; death starts a fresh meter. No new payload or per-tick hunger packet is needed.

## Data, persistence, and death

`TerragolemFuelData` is a dedicated serialized player attachment, not a static UUID inventory map and not part of general-purpose ability state. It contains the nine-slot handler, an empty reserved power-core field, and the repair cooldown timestamp.

Use stable NBT keys `FuelInventory`, `PowerCore`, and `NextRepairTick`. Serialize with `ItemStackHandler.serializeNBT(HolderLookup.Provider)` and deserialize using the matching provider-aware method. Missing fields must be safe for existing worlds. Serialized input cannot resize the fixed inventory. Validate loading and insertion paths.

Fuel storage persists across closing, logout/login, restart, dimension changes, and switching genes. Switching away disables/closes the menu without deleting fuel. Non-death cloning deep-copies attachment data.

**Storage no longer persists through death.** Add its contents and the open fix slot to the normal living-drops collection, clear the handlers, and do not restore fuel in death clones. This applies even after changing genes and even with `keepInventory` enabled. Other mods may process the normal drops event.

Entity attachments are serialized with player saves and do not need chunk/block-entity dirty flags. Use normal menu slot synchronization while open, not full-inventory broadcasts every tick.

## Tags, items, and recipes

Define `TerragolemTags.Items.TERRAGOLEM_FUEL` once. The tag at `data/jeecore/tags/item/terragolem_fuel.json` has `replace: false` and contains:

- `minecraft:coal`
- `minecraft:charcoal`
- `jeecore:refined_coal`
- `jeecore:powered_coal`

Storage handler validation, menu slots, and quick move all enforce the tag. Repair additionally checks the supported four item identities. Reject invalid insertion through placement, drag, number-key swaps, shift-click, and handler access.

Register both custom items through `ModItems` on the mod bus. They use base `Item`, stack to 64, have no vanilla food components, and appear in the JeeCore creative tab. Provide English names, generated inventory models, and textures. Item artwork is reserved for the user; preserve the existing replaceable PNG files.

Refined coal recipe, ID `jeecore:refined_coal`, yields one item from exactly four ordinary coal:

```text
CC
CC
```

Powered coal recipe, ID `jeecore:powered_coal`, yields one item from four refined coal and four redstone, with an empty center:

```text
TRT
R R
TRT
```

Use checked-in 1.21.1 JSON with singular `recipe`, `advancement`, and `tags/item` directories. Recipe unlock advancements are included.

## Menu, input, and screen

Reuse the existing `Alternative / Toggle Ability` key (X). Consume its click for the local client player. Terragolem sends a zero-data `OpenTerragolemRepairMenuPayload`; preserve existing Nimbus and Infernal behavior for other genes. Do not register another key.

The server validates gene and life state and opens the registered player-bound menu. The client never opens storage independently. `stillValid` requires the owner, a living player, and Terragolem; no block-distance check applies. Close invalid menus on death or gene change.

Menu layout:

- Left: 3×3 persistent fuel grid.
- Near the grid: disabled power-core placeholder, always empty, rejecting insertion and extraction; tooltip `Power Core (Coming Soon)`.
- Right: clickable body diagram, with the unlabeled temporary Fix Fuel slot to its left and a thick horizontal connector touching the left arm.
- Bottom: player inventory and hotbar.

Shift-click player fuel into storage; storage and fix-slot stacks shift-click back to player inventory. Do not shift-click into the disabled power slot. Non-fuel player items remain in player inventory. Return remainder stacks correctly.

The body is a screen control, not a slot. Preserve the eyeless head. Tint by clamped health/maxHealth with piecewise interpolation: dark red at zero, amber/yellow at half, green at full. Reset render color after drawing. Click bounds follow the visible silhouette, excluding transparent gaps.

Hide the repair title and health/hunger/saturation labels. Only hovering the visible body silhouette activates the linked highlight. The slot reuses the storage slots' exact shading with no additional border; its interior and connector lighten neutrally. Hovering the slot/connector or keyboard focus does not trigger the repair highlight. Repair activation requires a mouse click on the body. Use the concise tooltip `Repair (+x ♥)`, with x equal to selected fuel's base healing at 100% quality (2, 3, or 3 hearts; 0 if no usable fuel). Dim/disable for full health, zero hunger, missing fuel, cooldown, or invalid menu.

Send only the normal inventory button request with current container ID. `clickMenuButton` invokes server validation and repair. Client state is predictive only; it never mutates authoritative health, hunger, saturation, heat, or items.

## Terragolem-only hunger HUD

The client-only `TerragolemFoodHudMixin` substitutes only food sprites for Terragolem while retaining vanilla positioning, animation, and visibility. Other genes retain normal sprites. Keep it in the mixin config's client list so dedicated servers never load it.

User-editable transparent 9×9 PNGs live under `assets/jeecore/textures/gui/sprites/hud/terragolem/`:

- `food_empty.png`, `food_half.png`, `food_full.png`.
- `food_empty_hunger.png`, `food_half_hunger.png`, `food_full_hunger.png` for the Hunger effect.

Do not use `assets/minecraft` overrides for this feature because they affect every gene. Preserve user-supplied artwork.

## Natural regeneration and heat integration

Gate only the two natural-regeneration branches through their `Player.isHurt()` checks in mapped 1.21.1 `FoodData.tick(Player)` using `FoodDataMixin`, with exactly two required targets. Keep the gene check in a static helper. Skip both healing and its associated regeneration exhaustion for Terragolem: suppressing heal alone wasted saturation. Leave normal activity exhaustion, hunger drain, starvation, difficulty behavior, and synchronization intact. Do not blanket-cancel `LivingHealEvent`: commands, potions, beacons, Dryad effects, and powered-fuel regeneration must work normally.

Register `jeecore.mixins.json` in mod metadata. Keep client mixins separate from common mixins.

`TerragolemOverheatManager.reduceHeat` runs server-side, reads runtime heat, clamps to 0–MAX_OVERHEAT, calls the existing setter, applies existing unlock rules, and immediately synchronizes if heat changed. Do not update just the attachment integer or client data; heat/lock maps must remain consistent.

### Primary heat balance

Left bursts add 60/120/160 heat for 1-2/3-4/5-6 charged segments. Held beams add 3.5 heat per tick (70 per second). The four-second inactivity delay and accelerating passive cooling remain. After overcharge, the primary lock halves that passive cooling rate until heat reaches zero; zero heat clears the lock and restores normal cooling, including when reached by powered coal.

During a held beam, holding physical right click with powered coal in either hand attempts normal item use every four ticks when hungry and off cooldown. The server enforces the shared 32-tick eating cooldown. Cooling subtracts from current authoritative heat immediately; subsequent beam ticks add heat to that reduced value. Main hand takes priority if both hands hold powered coal. Releasing the button, opening a screen, or stopping the beam stops these attempts.

### Furnace fuel and refueling visuals

Refined coal is registered through NeoForge's furnace fuel data map with 6,400 base burn ticks, four times coal's 1,600. Furnace, smoker, and blast furnace apply their normal processing/burn-rate rules, preserving the four-times-coal ratio (32 standard items per refined coal). Other furnaces using NeoForge's fuel lookup inherit the same value.

Accepted direct fuel consumption emits item-texture crumbs in front of the mouth, visible in first and third person and to nearby players. Coal/charcoal, refined, and powered coal emit respectively 12/16/20 crumbs, 4/8/12 thin, tall glowing orange vertical streaks drifting upward, and 4/8/12 smoke particles. Server particle packets include the eater. Snapshot the item before consumption so the last item still has its proper particle texture. Full hunger, cooldown, unsupported fuel, and non-Terragolem interactions produce no refueling effect.

## Implementation checklist

- [x] Halve saturation restored by all four coal fuels while retaining hunger, cooldown, and cooling behavior.
- [x] Apply the white-core/orange-halo streak design to every fuel, retaining tier colors, brightness, and counts.

- [x] Reduce streak counts to 4/8/12, halve bar width and increase height by 60%; brighten refined streaks and render all fuel streaks with an orange halo and narrow white core.

- [x] Scale streak color and emission by fuel: pale translucent orange for coal/charcoal, richer orange for refined, and saturated fully bright orange for powered coal.

- [x] Register refined coal as furnace fuel lasting four times coal through the shared fuel data map.
- [x] Add server-confirmed item eating particles for first/third person and nearby observers.
- [x] Add custom rising orange streaks and smoke with increasing counts by coal tier.


- [x] Scale burst fuel costs by charge tier: 2/3/4 food-point equivalents (8/12/16 exhaustion), spending saturation before hunger.

- [x] Increase burst and held-beam heat gain to 60/120/160 and 3.5 per tick.
- [x] Halve passive cooling after overcharge until zero heat restores the normal rate.
- [x] Make powered-coal eating remove a quarter of maximum heat and support normal item use during a held beam.


- [x] Animate user-supplied Powered Coal with a three-frame 16x48 ember-pulse strip and adjacent animation metadata (0,1,2,1; eight ticks/frame; interpolation). Preserve the original under artwork/item-originals and keep alpha/dark pixels unchanged.

- [x] Use vibrant red Miss, lime-green Hit, and vivid green Perfect text without white highlighting; render Fuel required in pale red. Preserve bold, small, shadow-free typography and white result text.

- [x] Add small bold shadow-free white body-centered results, colored Hit/Miss/Perfect feedback with Perfect ding, a five-second gray miss lockout at 50% healing, and a fuel-required message with red slot flash.

- [x] Implement three-target server-validated repair rounds with one reserved fuel, timed Hit/Perfect scoring, 50–150% healing, normal menu synchronization, GUI rings/sparks/sounds/results, and safe abort/death handling.

- [x] Increase primary fuel costs and route them through vanilla exhaustion so saturation drains before hunger; buff coal repair to 2 hearts and refined/powered repair to 3 hearts.

- [x] Silently clear charge, held beam, aftermath, stun locks/modifiers, action bar, flare, flash, and FOV state on death, respawn/clone, logout, and dimension transitions. Death also resets heat; dimension travel preserves accumulated heat and stored fuel. Reject dead-player charge input and prevent lethal overheat from recreating state after cleanup.

- [x] Reuse storage-slot shading for the fix slot, remove its extra border, and restrict the linked neutral hover effect and repair activation to the body.

- [x] Rename all coal registrations, constants, recipe/advancement IDs, models, texture files, tags, localization keys, and documentation to Refined Coal (middle tier) and Powered Coal (highest tier). Preserve user artwork bytes.

- [x] Apply latest balance: coal 2 hearts, refined 3, powered 3 plus 5-second regeneration; repair cooldown 2 seconds.
- [x] Move unlabeled fix slot left of body with a thick connector and shared hover highlighting.
- [x] Apply exactly 30-point powered cooling on direct consumption and successful repair.

- [x] Add uniform left-shot hunger cost, steady held-beam drain, and larger overcharge/timeout penalties with persistent partial hold usage.

- [x] Register custom items, creative visibility, localization, models, textures, recipes, unlocks, and fuel tag.
- [x] Centralize direct tagged-fuel consumption using vanilla `FoodData.eat(FoodProperties)` and the reference food values.
- [x] Prevent phantom natural-regeneration saturation costs, add a shared 32-tick eating cooldown, and block full-hunger use/animation.
- [x] Add persistent separate storage attachment and non-death clone handling.
- [x] Register menu with filtered storage, disabled power placeholder, inventory, and quick move.
- [x] Route the existing toggle key through a validated server open-menu payload.
- [x] Render the health-tinted, eyeless body; hide title/stat labels.
- [x] Add temporary Fix Fuel slot, lowest-tier fallback, and safe close/overflow handling.
- [x] Replace hunger/saturation costs with hunger > 0 eligibility and one-item payment.
- [x] Implement 2/3/3-heart repairs, powered Regeneration I for 5 seconds, and server cooldown.
- [x] Add hover highlighting and concise selected-fuel heart tooltip.
- [x] Drop stored and temporary fuel on death and prevent restoration/duplication on death clone.
- [x] Intercept only vanilla food-based natural healing.
- [x] Add authoritative powered-coal heat reduction.
- [x] Add Terragolem-only replaceable hunger HUD sprites.
- [x] Compile Java and process resources after the September 27 code changes.
- [ ] Acceptance/gameplay tests and dedicated-server/two-client checks: left to the user as requested.

Completion marks indicate implementation, not passing gameplay tests.

## Acceptance tests — pending user execution

### Items and direct consumption

- [ ] Four coal craft one refined coal; four refined coal plus four redstone craft one powered coal with the center empty.
- [ ] Custom items have names, models, textures, and creative visibility; the tag contains the four required items.
- [ ] Non-Terragolem players cannot eat the four fuels; Terragolem can use either hand.
- [ ] Direct consumption grants hunger/saturation of 3/1.8 for coal/charcoal, 6/7.2 for refined, and 8/6.4 for powered, with correct clamping; existing saturation is not halved.
- [ ] Creative eating preserves the held stack.
- [ ] Powered coal removes 125 heat points (clamped to zero) and updates the HUD immediately, when hungry; full hunger rejects all fuel use without consumption, cooling, sound, or swing.
- [ ] Ordinary food and cake remain blocked; unknown tagged fuels grant no nutrition.

### Storage, temporary slot, and lifecycle

- [ ] Each player sees only their storage; two clients cannot mutate each other's contents.
- [ ] Storage rejects non-tagged items through placement, drag, number-key swap, shift-click, and handler insertion.
- [ ] Tagged items can be inserted/extracted; the power placeholder rejects all items.
- [ ] Storage survives menu close, logout/restart, dimension changes, and gene switching.
- [ ] Every newly opened menu has an empty fix slot; only the four supported tagged fuels can enter it.
- [ ] Closing returns the entire unused fix stack to storage; full storage falls back to inventory, then world drops, without loss or duplication.
- [ ] Death drops storage and temporary fix fuel exactly once, including with keepInventory and after switching genes. Respawn has empty storage.
- [ ] Gene changes close/disable the menu without deleting stored fuel.

### Repairing and UI

- [ ] Verify body-centered result text and colored feedback without shadows or active-round instructions below the body.
- [ ] Verify missed clicks/timeouts end at 50% healing with a five-second gray lock that survives reopening.
- [ ] Verify clicking without fuel shows Fuel required and flashes the fix slot red.

- [ ] One body click starts a round without immediate healing and reserves exactly one fuel item.
- [ ] Three sequential targets use distinct body regions, with shrinking rings and Hit/Perfect/Miss feedback.
- [ ] Server timing and target proximity determine points; repeated clicks, stale target tokens, forged scores, and wrong container IDs cannot grant extra attempts or healing.
- [ ] Zero points heals 50%, three regular hits heals 100%, and three Perfect hits heals 150% of base, clamped to missing health.
- [ ] Completion shows quality and actual hearts restored and starts the two-second cooldown.
- [ ] Rearranging or removing other fuel during a round cannot change its reserved item or reward.
- [ ] Closing/reopening, disconnecting, changing dimensions/genes, hunger falling to zero, or external full healing cancels without a reward, returns fuel safely, and applies cooldown.
- [ ] Death during any target drops the reserved fuel once alongside storage/fix fuel and cannot duplicate it on close/respawn.

- [ ] Refined fuel remains preferred over powered when falling back to storage despite equal healing amounts.
- [ ] Only body hover highlights the body, unlabeled fix slot, and thick connector; the connector lightens without yellow. Fix-slot shading matches storage with no extra border, and slot interaction only moves items.

- [ ] At missing health and hunger above zero, a completed 100%-quality round consumes exactly one reserved fuel and heals 2/3/3 hearts by tier, capped to missing health.
- [ ] Powered repair adds Regeneration I for 5 seconds and removes exactly 30 heat points, clamped to zero.
- [ ] Repairs neither deduct nor add hunger/saturation; zero saturation is sufficient.
- [ ] Full health, zero hunger, no supported fuel, or cancelled healing consumes nothing and grants nothing.
- [ ] Explicit fix-slot fuel wins; otherwise coal/charcoal wins over refined, which wins over powered, regardless of storage arrangement.
- [ ] Unsupported tagged storage items are skipped without errors or consumption.
- [ ] Rapid clicks, reopening, forged button IDs, and stale menu requests cannot bypass server validation/cooldown.
- [ ] Body color tracks health, hover highlights the silhouette, and tooltip reports selected fuel hearts. No eyes, title, or stat labels appear.
- [ ] Terragolem uses its custom hunger sprites, other genes use vanilla, and gene switching updates selection.

### Heat balance acceptance checks

- [ ] Left shots add 60/120/160 heat by tier; held beams add 70 per second, capped at 500.
- [ ] After overcharge and the inactivity delay, cooling is half the normal accelerating rate and remains slower below maximum until zero.
- [ ] Reaching zero through cooling or powered coal unlocks the primary and restores normal cooling for the next activation.
- [ ] While holding a beam, powered coal in either hand removes 125 current heat, consumes one item, and respects hunger/cooldown; subsequent ticks continue adding heat from the reduced value.
- [ ] Releasing right click, stopping the beam, death, dimension travel, or opening a screen stops held-beam eating attempts. Full hunger and active cooldown grant no cooling or swing.

### Ability fuel consumption

- [ ] With saturation available, ability exhaustion drains saturation without directly lowering hunger; after saturation runs out, normal food ticks drain hunger.

- [ ] Valid left shots at 1-2/3-4/5-6 segments add 8/12/16 exhaustion (2/3/4 food-point equivalents), respectively; rejected/early clicks cost nothing. Verify saturation is depleted before hunger at every tier.
- [ ] Right hold adds 4 exhaustion per 20 active ticks and stops charging after release.
- [ ] Short holds accumulate across activations and relogs instead of resetting the partial charge.
- [ ] Maximum-heat overcharge adds 24 exhaustion exactly once; charge timeout adds 16 exhaustion exactly once.
- [ ] Normal release, cancellation, and generic stun application add no penalty costs.
- [ ] Hunger never goes negative, saturation stays within hunger, and unrelated genes/creative players are unaffected.

### Charge lifecycle regression

- [ ] Die while charging, in the warning window, holding a beam, or in aftermath: no retained charge, flare, beam damage, movement/jump lock, flash, or FOV effect after respawn.
- [ ] Lethal overheat backlash does not recreate the primary lock or aftermath state after death cleanup.
- [ ] Travel between dimensions during charge or beam hold (including End return): ability cancels without release stun, timeout, shot, or extra fuel penalty; stored fuel and existing heat remain intact.
- [ ] Observers in the old dimension see the effects stop; the player and destination observers see no stale charge.
- [ ] Logout during charge/hold stops the beam and does not restore it on login.
- [ ] Other mods cancelling death/travel before the cleanup handler do not trigger cleanup; normal ability release/timeout still works.

### Regeneration regression and server compatibility

- [ ] Terragolem does not heal through high-saturation or slow high-hunger natural regeneration.
- [ ] Idle injured Terragolem no longer loses saturation to blocked natural regeneration; normal activity exhaustion and starvation still operate normally.
- [ ] Rapid right-clicks and alternating fuel types/hands cannot bypass the shared 32-tick eating cooldown. Rejected uses never animate.
- [ ] Potion, command, beacon, Dryad, and powered-fuel regeneration healing remain functional.
- [ ] Other genes retain vanilla natural regeneration.
- [ ] Dedicated-server startup does not load client screens or HUD mixins.

## Deliberately deferred work

- Power-core items, tags, upgrade effects, and power consumption.
- Automated fuel burning or feeding from storage.
- Additional repair animations beyond the implemented rings, sparks, sound, and quality feedback.
- Additional balance changes beyond the user's specified food equivalents and fuel-based repair revision.

## Furnace and refueling visual acceptance checks

- [ ] Refined coal is accepted in furnace, blast furnace, and smoker fuel slots; one item processes 32 standard recipes versus coal's 8, accounting for each furnace's speed.
- [ ] Compatible modded furnaces using NeoForge fuel lookup recognize refined coal at four times the coal burn value.
- [ ] Eat each fuel in first person, rear/front third person, and with a nearby observer: correct item crumbs, upward orange bars, and smoke appear; higher tiers emit more particles.
- [ ] Eating the final item in either hand still shows its texture; powered coal consumed during beam hold also emits the effect.
- [ ] Full hunger, active cooldown, and non-Terragolem use produce no eating particles or refueling effects.
- [ ] Dedicated server starts without loading the client particle class; resource reload resolves the spark sprite without missing textures.

- [ ] Compare refueling in daylight and darkness: coal/charcoal streaks are pale and soft, refined streaks are richer, and powered streaks are saturated and fully bright; all still drift upward and fade.

- [ ] Verify fewer, thinner, taller streaks at all tiers; refined is brighter, and every fuel has a visible white center surrounded by an orange halo while rising and fading.
