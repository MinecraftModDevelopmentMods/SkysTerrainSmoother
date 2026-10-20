# Sky's Terrain Smoother

Sky's Terrain Smoother adds smaller transitions to new Overworld terrain,
including cave floors. It uses slabs, steps and corners from Sky's Grass Slabs
and Sky's Building Pieces, with matching materials for grass, dirt, stone,
sandstone, sand and hardened clay.

## Smoothing levels

Set `worldgen.smoothingLevel` in `config/skysterrainsmoother.cfg`:

1. **Slabs** — half-block transitions.
2. **Slabs and steps** — adds pieces covering half a slab's footprint.
3. **Slabs, steps and corners** — also uses pieces covering a quarter of a
   slab's footprint.

Every smoothing piece is half a block tall. If a material doesn't have steps
or corners, it still uses its slab at higher levels.

Only new, dry terrain is smoothed. Existing chunks, underwater floors and cave
ceilings stay unchanged. Cave passages need three clear blocks above the floor,
so low tunnels keep their headroom. To see the effect in an existing world,
travel beyond the terrain you've already generated.

## Sand and other materials

Ordinary sand and red sand get their own horizontal slabs. Three matching sand
blocks in a row make six slabs, and two slabs side by side make a full block.
Like sand blocks, the slabs fall when unsupported. Sand uses slabs at every
smoothing level.

Mineralogy is supported without another add-on. When it's installed, available
natural rock slabs are used by default. It doesn't get new steps or corners
from this mod, and disabled slab types are skipped.

Ores, gravel, wood and decorative stone aren't smoothing materials.

For BOP grass and soils, add the
[Terrain Smoother Biomes O' Plenty add-on](https://github.com/MinecraftModDevelopmentMods/SkysTerrainSmoother-BiomesOPlenty-Addon).

## Installation and settings

For **Minecraft 1.10.2**, use **Forge 12.18.3.2511** and **Java 8**, with:

- Sky's Grass Slabs **1.1.0.110021**.
- Sky's Building Pieces **0.3.6.110021**.
- Sky's Terrain Smoother **0.1.0.110021**.

Install all three main jars on both the client and server.

Smoothing starts at level 1. Stop the game or server before editing the
configuration, then restart. You can turn smoothing off or disable Mineralogy
support; see [configuration](docs/CONFIGURATION.md).

While installed, Terrain Smoother handles grass smoothing instead of Grass Slabs.
Turning its master switch off stops both passes. Grass Slabs' own
`worldgen.generateGrassSlabs` setting still decides whether grass is included.
Removing Terrain Smoother restores Grass Slabs' standalone generation.

## Existing worlds

Back up worlds before changing their mods. Turning smoothing off doesn't remove
pieces already placed, so keep the mods that provide them installed.
See [existing worlds](docs/WORLD-UPGRADES.md) before changing the mod list.

Release history is in the [changelog](CHANGELOG.md). For development, see
[building from source](docs/BUILDING.md) and the [add-on API](docs/API.md).

## Licence

[LGPL-2.1-only](LICENSE). See [NOTICE](NOTICE) for credits.
