# Nimble cloudburst status icon

The existing `src/main/resources/assets/jeecore/textures/mob_effect/nimble.png` was
replaced in place; the obsolete wind icon is no longer the active texture.
The replacement is a transparent 18x18 RGBA PNG with a four-color pale blue-white
palette, matching the existing effect texture dimensions. All Nimble levels use it.

Created with the built-in imagegen tool using the user's `cloudburst.png` as a
reference. `generated-source.png` preserves the output; `prepare-icon.ps1` crops,
normalizes with nearest-neighbor sampling, and quantizes the palette for the final
18x18 asset. `preview.png` is a 16x enlarged preview on a dark background.

## Generation prompt

Create a single Minecraft mob status effect icon based on the attached reference
cloudburst spritesheet. The reference is a 64x256 vertical sheet of 8 frames; use
the third visible smoke pose as the reference shape, not the whole sheet. Output
one icon only, no text, no grid, no border, no sheet. Pixel art at a strict logical
18 by 18 pixel grid, enlarged with nearest-neighbor square pixels if native 18x18
output is unavailable. Transparent background outside the icon. Center a compact
low-resolution pale icy-blue cloud smoke backburst, curling upward toward the
right, with tiny separated trailing puffs toward the lower left. Fit the main
silhouette within 16 by 14 logical pixels with transparent margins. Use only 4 pale
blue-white colors from the source art and crisp un-antialiased pixel edges. This
is the Nimbus Nimble I/II/III inventory status-effect icon. Preserve the reference's
puffy curling smoke identity, simplify aggressively for readability at 18x18. No
shadow, glow, extra objects, letters, or decorative framing.
