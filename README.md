# Sky's Terrain Smoother

Sky's Terrain Smoother softens new Overworld slopes using pieces from Sky's
Grass Slabs and Sky's Building Pieces. It also provides ordinary and red sand
slabs for beaches and desert slopes.

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

Only newly generated dry terrain is changed, including cave floors. Cave
passages need three clear blocks above the floor before a piece is added, so
two-block tunnels stay walkable. Ceilings, existing chunks, underwater terrain,
structures and unsupported materials are left alone.
Initial materials are grass, ordinary dirt, raw stone variants, sand, red sand,
sandstone, red sandstone and hardened clay. Mineralogy's available natural rock
slabs can be used too. Ores, gravel, wood and decorative stone are not smoothed.
Sand slabs are horizontal only and fall when unsupported. Three matching sand
blocks in a row make six slabs; two matching slabs side by side make one block.
Sand uses slabs at all smoothing levels.

Grass oasis patches and sand added during biome decoration receive matching
pieces too. Hardened clay uses Building Pieces' existing slabs, steps and
corners. If beach decoration changes the terrain to sand, the new cap uses
matching sand instead of grass or stone. Ore veins can remain hidden beneath
a piece when generation replaces its support with ore; neither the ore nor the piece is
removed. Other unsupported replacements such as gravel remove the cap without
restoring overwritten terrain. Existing saved pieces are not
repaired or regenerated. The separate Biomes O Plenty terrain add-on adds
matching BOP soil and grass pieces for all three levels.

Terrain Smoother takes over generation from Grass Slabs while installed.
Turning Terrain Smoother off disables both generation passes; removing it
restores Grass Slabs' normal independent generation. Grass Slabs' grass
generation setting still controls whether grass terrain is included.

See [configuration](docs/CONFIGURATION.md), [world upgrades](docs/WORLD-UPGRADES.md),
[building from source](docs/BUILDING.md) and the [add-on API](docs/API.md).
Legacy recovery remains the responsibility of the content mods.

[Release preparation](docs/RELEASING.md) covers the CI checks and publication safeguards.

Licensed under LGPL-2.1-only.
