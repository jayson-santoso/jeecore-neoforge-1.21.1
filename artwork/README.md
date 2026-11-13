# Powered Coal animation

`item-originals/powered_coal-original.png` is the untouched user artwork.

The game texture at `src/main/resources/assets/jeecore/textures/item/powered_coal.png`
is now a 16x48 vertical strip containing three 16x16 frames. Its adjacent `.png.mcmeta`
plays frames 0, 1, 2, 1 with interpolation at eight game ticks per frame (1.6 seconds per loop).
Only the existing warm ember pixels brighten. The first frame is the original artwork;
transparency, silhouette, and dark coal pixels are preserved in every frame.

The generated pulse reference is kept in `animation-references/`. To rebuild the strip
after editing the 16x16 original, run `./scripts/assets/build-powered-coal-animation.ps1` in PowerShell 7.
Alternatively, hand-edit the three frames directly in the game texture.

To restore a static item, copy the original back over the game texture and remove
`powered_coal.png.mcmeta`. Refined Coal's artwork is unchanged.
