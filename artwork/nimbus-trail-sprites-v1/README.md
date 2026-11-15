# Nimbus wind trail sprites v1

Original Nimbus pixel-art asset, based on the concept reference in this chat.
Generated artwork: built-in imagegen. Mechanically exported, palette-cleaned, and registered on a fixed pixel grid.
No gameplay code, resource registration, or renderer implementation was changed.

## Deliverables

- nimbus_wind_trail.png: transparent RGBA vertical sprite sheet, 128 x 128 pixels.
- Each of 8 unique frames is 128 x 16 pixels, top to bottom.
- frames/: individual transparent frame PNGs.
- nimbus_wind_trail.png.mcmeta: animation metadata for an atlas-managed texture.
- nimbus_wind_trail_preview.gif: enlarged 6x looping preview on an opaque checkerboard.
- nimbus_wind_trail_contact_sheet.png: enlarged frame inspection sheet on checkerboard.
- validation.json: dimensions, alpha, padding, and loop checks.

The PNG and individual frames have true transparency. The checkerboard exists only in the previews.
The sprite has its thick root on the LEFT and its thin tail on the RIGHT.
Artwork uses three flat near-white/icy-blue colors, with hard edges and no antialiasing.

## Playback

Frame order: 0,1,2,3,4,5,6,7,6,5,4,3,2,1, then repeat.
This is a gentle ping-pong ripple loop, not continuously backward-scrolling texture motion.
Each frame lasts 2 Minecraft ticks (100 ms at 20 TPS); a complete cycle lasts 1.4 seconds.
There is no duplicated endpoint pause. The wrap transition is the same neighboring pose pair as the first transition.
Use nearest-neighbor sampling with frame interpolation disabled.

A directly bound custom texture still needs frame UV selection in its renderer.
The .mcmeta is useful when the texture is loaded through Minecraft's animated texture atlas system.
These assets are staged in artwork only and are not installed in the mod.
