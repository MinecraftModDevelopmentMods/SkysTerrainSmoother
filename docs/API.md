# Add-on API, version 1

Use `zone.moddev.mc.skysterrainsmoother.api.TerrainSmoothingApi` during your
mod's initialization or post-initialization. Declare ordering after
`skysterrainsmoother`. Registration closes at load completion.

Create an immutable `TerrainMaterial` with a namespaced logical identity, a
set of exact full-block source states and a bottom slab state. Optionally
supply arrays of four bottom steps and four bottom corners. Array order is
north, east, south, west; corners use northwest, northeast, southeast,
southwest. Steps occupy one quarter of a block's volume and corners one eighth.
Every output must be half a block tall, non-tile, and match that footprint.

```java
TerrainSmoothingApi.registerMaterial(new TerrainMaterial(
    new ResourceLocation("example", "natural_rock"),
    Collections.singleton(naturalRock.getDefaultState()),
    bottomSlab, null, null, false, null));
```

`addShapes(materialId, steps, corners)` supplies missing forms on an existing
mapping. It cannot replace that mapping's slab or existing finer forms. A
Mineralogy add-on can therefore extend a built-in `mineralogy:<rock>` mapping
without replacing its native slab. Do not assume the mapping exists when the
player has disabled Mineralogy support or its slab registration. Use
`hasMaterial(materialId)` to check before contributing. The Mineralogy setting
also applies to mappings supplied by add-ons for Mineralogy source blocks.

`addBiomeRule` restricts material eligibility. `addAreaProtection` vetoes a
position by dimension, position and biome. Neither exposes a mutable world or
permits an add-on to override player settings. Callbacks should be deterministic
and must not load chunks or write to the world.

Conflicting sources, output mappings, invalid geometry, tile entities and late
registration are rejected. A rejected contribution leaves existing mappings
unchanged. Mutable input arrays and sets are copied. The engine resolves
registered neighbouring pieces to their logical full surface before comparing
heights, and writes only within the owning chunk.

Mappings apply to dry cave floors as well as outdoor terrain in new chunks.
Underground targets require three clear blocks above their supporting floor;
ceiling and wall shaping is not supported. Material and area restrictions
apply at each floor's actual position. Ores remain outside the source
catalogue, but a late ore replacement beneath a generated piece is preserved.

Grass-like material mappings should provide their full dirt support state in
`supportAfterPlacement`. Register matching content orientations through the
Grass Slabs version 1 grass API if their lifecycle should participate
in its shared spreading rules. Content mods still own placement, drops,
rendering and migration.

Building Pieces' `BuildingPiecesApi.bottomPiece` looks up existing shapes by
material and logical direction, without exposing palette IDs or metadata.
A lookup can return no state where that material has no corresponding shape.

Only the `api` package is an extension contract. Engine classes are internal
implementation details and are not a supported API.
