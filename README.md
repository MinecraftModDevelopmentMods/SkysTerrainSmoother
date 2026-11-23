# Sky's Terrain Smoother

Sky's Terrain Smoother softens the block-high edges of new Overworld terrain,
including cave floors. It uses matching slabs, steps and corners to make slopes
easier to walk across, while keeping the original terrain underneath.

## Smoothing levels

Choose a level in **Mods > Sky's Terrain Smoother > Config**:

1. **Slabs** — half-block transitions.
2. **Slabs and steps** — adds pieces covering half a slab's footprint.
3. **Slabs, steps and corners** — also uses pieces covering a quarter of a slab's footprint.

Level 3 is the default. Broad, gentle terraces still use slabs for a wider
tread; steeper hills use the smaller shapes where they fit. Cave passages keep
their headroom, and missing steps or corners fall back to slabs where safe.

Only new, dry terrain is smoothed. Saved chunks, underwater floors, cave
ceilings and protected structures stay unchanged. In an existing world, travel
beyond the chunks you have already generated to see the effect.

## Materials

The built-in mappings cover grass, dirt, natural stone variants, sandstone,
terracotta, sand, red sand, gravel, snow blocks, packed ice and powder snow.
The matching Forge build also supports Mineralogy's natural rocks, using its
native slabs and Building Pieces' steps and corners without another add-on.
There is no matching Mineralogy release for this early NeoForge build.

Snowy ground gets snowy transitions from the start. Sand and gravel slabs fall
when unsupported. Powder-snow pieces keep their sinking and freezing behaviour,
including the ability to walk on them in leather boots.

Sand and gravel have horizontal slabs only. Three matching full blocks make
six slabs; two matching slabs make a full block. A powder-snow bucket makes two
slabs and returns the empty bucket. Powder-snow pieces can be cut into smaller
forms and combined again; rebuilding a full bucket also needs an empty bucket.

Ores, wood and decorative stone are not smoothing materials. Ores generated
beneath an existing transition keep that transition in place.

For Mineralogy's natural rock slabs, steps and corners on Minecraft 1.20.1,
use the Forge build. The Mineralogy jars tested on Forge require a later
Forge loader than this NeoForge build provides.

For BOP soils and stone, install the
[Terrain Smoother Biomes O' Plenty add-on](https://github.com/MinecraftModDevelopmentMods/SkysTerrainSmoother-BiomesOPlenty-Addon)
and its dependencies.

## Installation and settings

For **Minecraft 1.20.1**, use **NeoForge 47.1.106** and **Java 17**, with:

- Sky's Grass Slabs **1.2.1.120012**.
- Sky's Building Pieces **0.3.10.120012**.
- Sky's Terrain Smoother **0.1.4.120012**.

Install all three main jars on both the client and server. Worlds using the
Forge Mineralogy build should stay on Forge 47.4.10 for Minecraft 1.20.1.

The Config screen has separate pages for the smoothing level, material groups
and Mineralogy support. For example, you can turn off stone smoothing while
keeping soil, sand and gravel enabled. Save with **Done**, then restart
Minecraft. A dedicated server uses its own configuration file.
See [configuration](docs/CONFIGURATION.md).

While installed, Terrain Smoother handles grass smoothing instead of Grass
Slabs. Turning the master switch off stops both passes. Grass Slabs' own
`worldgen.generateGrassSlabs` setting still decides whether grass is included.
Removing Terrain Smoother restores Grass Slabs' standalone generation.

## Existing worlds

Back up worlds before changing their mods. Turning smoothing off does not
remove pieces already placed, so keep the mods that provide them installed.
An interrupted generation repair can finish after a restart, but only for the
unchanged placements it recorded. It does not rescan old terrain or overwrite
player edits. See [existing worlds](docs/WORLD-UPGRADES.md).

Release history is in the [changelog](CHANGELOG.md). For development, see
[building from source](docs/BUILDING.md) and the [add-on API](docs/API.md).

## Licence

[LGPL-2.1-only](LICENSE). See [NOTICE](NOTICE) for credits.
