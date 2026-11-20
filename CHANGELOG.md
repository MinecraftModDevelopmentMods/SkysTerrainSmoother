# Changelog

## 0.1.2.110021

- Spread late terrain repairs over server ticks, and keep unfinished work across unloads and restarts.
- Recorded work leaves player edits and unrecorded saved terrain alone.


## 0.1.1.110021

Snow and packed-ice terrain can now be smoothed with the existing Building Pieces slabs, steps and corners.

- Used Building Pieces' built-in Mineralogy steps and corners automatically
  at levels 2 and 3. No Mineralogy add-on is required.

- Made level 3 the default for new configurations. Existing choices are kept.
- Added separate development launch profiles for testing with Mineralogy and OreSpawn.

- Kept the visible ground grassy beneath generated steps and corners instead of turning the whole support block to dirt.

- Kept the Mods page and Config button working after Eclipse rebuilds.
- Added help text for every config option.

- Let thin snow give way to valid transitions on new snowy slopes, while leaving
  deeper snow and occupied terrain alone.
- Used fitting steps and corners on mixed uphill/downhill ledges instead of
  skipping the whole placement. The protection against full-height slab jumps remains.

- Stopped consecutive slab caps from recreating full-block jumps on narrow
  mountain ledges. Wider terraces and fitting steps or corners still smooth normally.

- Added a Config screen to the Mods list. Changes take effect after restarting.
- Fixed gaps in new slopes when nearby decoration had already placed grass or
  flowers. Trees, crops and occupied blocks are left alone; saved terrain is unchanged.

## 0.1.0.110021

- Added hardened clay transitions and smoothing for grass oasis patches added during decoration.
- Added smoothing on new cave floors, while preserving tunnel headroom.
- Kept pieces above ore veins introduced later during generation.
- Added falling ordinary and red sand slabs, including recombination recipes.
- Kept beach caps matched to terrain changed by biome decoration.
- Added support for the separate Biomes O Plenty terrain add-on.

- Added three smoothing levels for new dry Overworld surfaces.
- Reused Grass Slabs and Building Pieces, with optional Mineralogy rock slabs.
- Added an extension API for materials, finer shapes and protected areas.
- Kept existing chunks unchanged and avoided duplicate grass generation.
