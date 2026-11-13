# Overheated status

Reaching Terragolem's maximum heat applies the harmful custom status Overheated. The existing eight-second severe stun, jump lock, nausea/darkness, initial screen flash, backlash damage, and primary lock remain. The status also applies its own 35% movement-speed reduction, so a milder slowdown remains after the initial severe stun expires.

The status has a heat-owned duration rather than a fixed timer: it remains active until the primary overheat lock clears when the heat bar reaches zero. Cooling fuel immediately removes it if it finishes draining the bar. Removing the status alone does not cool the body, so the heat manager reapplies it while still locked. Clearing heat removes only this status's attribute modifier; unrelated movement effects remain. A restored status without matching active heat is removed on the next server tick, avoiding stale debuffs after restart or gene changes.

The icon uses the vanilla fire-resistance flame artwork with the status name Overheated and a harmful category. Normal potion bubbles are disabled in favor of the custom layered flame sparks, dense smoke, and sparse vanilla flames.

## Shivering and burning visuals

While the status is active, a client-only mixin makes the player renderer use Minecraft's native isShaking branch: the exact body shiver used when fully frozen in powdered snow. There are no custom body offsets and no Overheated camera shake. The hook only affects rendering; frozen ticks, freezing damage, frost overlays, position, hitboxes, and aim are unchanged. It ends with the status and preserves genuine freezing shivers.

The existing malfunction renderer discovers overheated players directly from synchronized status state. Its flame/smoke emission retains a visible intensity floor while overheating, including late in the cooling period. Newly arriving observers therefore see the burning body even before the next heat update. Burning emissions stop when the status/heat state clears, while already emitted smoke fades naturally.

Vanilla effect packets synchronize application, removal/expiry, and new tracking observers, so both local and other-player visuals follow the same server-owned status. No per-frame shivering packets are sent.

## Validation

GameTests cover maximum-heat application, persistence past the initial eight-second severe stun, remaining movement slowdown, instant cleanup on cooling to zero, reapplication while still hot, new-observer synchronization, ordinary partial-heat exclusion, and stale/orphan cleanup. The native body-shivering appearance requires an in-game visual check.

Gradle build, Infernal regression checks, and all 57 required headless GameTests passed.

