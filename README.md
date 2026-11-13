# Sky's Terrain Smoother

Sky's Terrain Smoother adds smaller transitions to new Overworld terrain,
including cave floors. It uses slabs, steps and corners from Sky's Grass Slabs
and Sky's Building Pieces, with matching materials for grass, dirt, stone,
sandstone, sand, gravel, hardened clay, packed ice, full snow blocks and powder snow.

## Smoothing levels

Set `worldgen.smoothingLevel` in `config/skysterrainsmoother-common.toml`:

1. **Slabs** - half-block transitions.
2. **Slabs and steps** - adds pieces covering half a slab's footprint.
3. **Slabs, steps and corners** - also uses pieces covering a quarter of a
   slab's footprint.

Every smoothing piece is half a block tall. Gentle surface terraces use a
full-width slab when there is room on both sides of a one-block rise. If a
material doesn't have steps or corners, it uses its slab at higher levels
wherever a slab fits safely.

Narrow mountain ledges keep their downhill edge at its original height, rather
than gaining a row of slabs that recreates a full-block step. Wider terraces
still get transitions. At levels 2 and 3, the engine keeps a fitting uphill step
or corner even when a whole slab would raise the downhill edge.

A single layer of snow doesn't block an otherwise valid transition. Grass
slabs, steps and corners take on their snowy look alongside nearby snow and
on exposed cold ground as the terrain generates. Deeper snow, trees, crops
and occupied blocks stay untouched.

Only new, dry terrain is smoothed. Existing chunks, underwater floors and cave
ceilings stay unchanged. Cave passages need three clear blocks above the floor,
so low tunnels keep their headroom. To see the effect in an existing world,
travel beyond the terrain you've already generated.

## Sand and other materials

Gravel, ordinary sand and red sand have their own horizontal slabs. Three matching
blocks in a row make six slabs, and two slabs side by side make a full block.
Like sand blocks, the slabs fall when unsupported. Sand uses slabs at every
smoothing level.

Install the NeoForge build of Mineralogy to smooth its natural rock. When
its slabs are enabled, Building Pieces provides the matching steps and corners.

Ores, wood and decorative stone aren't smoothing materials.

## Snow and powder snow

Packed ice and snow blocks use matching Building Pieces slabs, steps and corners.
Powder snow has its own horizontal pieces: they retain sinking, freezing and
slow movement, while leather boots let you stand on them. The empty part of a
step or corner stays empty, including its fog and freezing effects.

One powder-snow bucket makes two slabs and returns an empty bucket. To fill it
again, craft two slabs with an empty bucket. Slabs cut into steps, steps cut
into corners, and matching vertical pairs rebuild the larger pieces. Breaking
a partial piece returns that piece; it cannot fill a whole bucket on its own.
Matching halves placed together become an ordinary full powder-snow block.

For ordinary snow slabs, craft two snow blocks diagonally to make four slabs.
Vanilla's three-block row still makes snow layers. Recipe-book buttons show one
result each; right-click for alternative ways to make the same item.

For BOP grass and soils, add the
[Terrain Smoother Biomes O' Plenty add-on](https://github.com/MinecraftModDevelopmentMods/SkysTerrainSmoother-BiomesOPlenty-Addon).

## Installation and settings

For **Minecraft 1.21.1**, use **NeoForge 21.1.247** and **Java 21**, with:

- Sky's Grass Slabs **1.2.1.121012**.
- Sky's Building Pieces **0.3.11.121012**.
- Sky's Terrain Smoother **0.1.2.121012**.

Install all three NeoForge jars on both the client and server. If you use
the BOP add-on, keep its matching dependencies installed too.

The old CFG settings are copied once into TOML; the original file is kept.
New configurations start at level 3. Existing saved choices are kept.
Select the mod in **Mods > Config** to choose a
level, turn smoothing off or open the material and mod-support pages, then restart Minecraft.
You can switch soil, sand and gravel, stone, snow and ice smoothing separately.
For example, turn **Stone and rocks** off to keep the softer ground smoothed
without changing rock terrain. This also covers Mineralogy and BOP rocks.
For a dedicated server, edit its configuration file and restart the server.
See [configuration](docs/CONFIGURATION.md).

While installed, Terrain Smoother handles grass smoothing instead of Grass Slabs.
Turning its master switch off stops both passes. Grass Slabs' own
`worldgen.generateGrassSlabs` setting still decides whether grass is included.
Removing Terrain Smoother restores Grass Slabs' standalone generation.

## Mineralogy steps and corners

Building Pieces 0.3.7 or later includes Mineralogy rock steps and corners.
Install Mineralogy alongside the usual Sky mods; no separate Mineralogy
add-on is needed. Level 1 uses native slabs, level 2 also uses steps and
level 3 also uses corners. Your Mineralogy support setting still applies.

## Existing worlds

Back up worlds before changing their mods. Turning smoothing off doesn't remove
pieces already placed, so keep the mods that provide them installed.
See [existing worlds](docs/WORLD-UPGRADES.md) before changing the mod list.

Release history is in the [changelog](CHANGELOG.md). For development, see
[building from source](docs/BUILDING.md) and the [add-on API](docs/API.md).

## Licence

[LGPL-2.1-only](LICENSE). See [NOTICE](NOTICE) for credits.
