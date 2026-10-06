# Sky's Terrain Smoother

Sky's Terrain Smoother softens new Overworld slopes using pieces from Sky's
Grass Slabs and Sky's Building Pieces. It adds no blocks or items of its own.

This beta is for Minecraft 1.10.2 and Forge 12.18.3.2511. Install Terrain
Smoother 0.1.0.110021, Grass Slabs 1.1.0.110021 and Building Pieces
0.3.0.110021 together on the client and server. Mineralogy is optional.

## Smoothing levels

- Level 1 uses bottom slabs.
- Level 2 also uses horizontal steps, each covering half a slab's footprint.
- Level 3 also uses corners, each covering a quarter of a slab's footprint.

All these pieces remain half a block tall. Where a material has no step or
corner, a slab is used instead. Disconnected or three-quarter footprints also
use a slab rather than a composite block.

Only newly generated dry surface terrain is changed. Existing chunks, caves,
underwater terrain, structures and unsupported materials are left alone.
Initial materials are grass, ordinary dirt, raw stone variants, sandstone and
red sandstone. Mineralogy's available natural rock slabs can be used too.
Ores, falling blocks, wood and decorative stone are not smoothed.

Terrain Smoother takes over generation from Grass Slabs while installed.
Turning Terrain Smoother off disables both generation passes; removing it
restores Grass Slabs' normal independent generation. Grass Slabs' grass
generation setting still controls whether grass terrain is included.

See [configuration](docs/CONFIGURATION.md), [world upgrades](docs/WORLD-UPGRADES.md),
[building from source](docs/BUILDING.md) and the [add-on API](docs/API.md).
Legacy recovery remains the responsibility of the content mods.

Licensed under LGPL-2.1-only.
