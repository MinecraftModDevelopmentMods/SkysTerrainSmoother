# Changelog

## 0.1.0.116051 (unreleased)

- Bring surface and cave smoothing to Minecraft 1.16.5.
- Use the matching Grass Slabs and Building Pieces versions.

## 0.1.0.115021 (unreleased)

- Bring surface and cave smoothing to Minecraft 1.15.2.
- Use the matching Grass Slabs and Building Pieces releases.

## 0.1.0.114041

- Added Minecraft 1.14.4 support, including cave floors and all three smoothing levels.
- Kept existing sand slabs, colours, orientations and custom item data when upgrading from 1.12.2.
- Moved settings to TOML while preserving choices from the old CFG file.
- Added the native Mods Config screen and kept changes pending until Minecraft restarts.
- Kept Mineralogy's slabs and Building Pieces' steps and corners working together.

## 0.1.0.112021

- Added ingredient-based recipe unlocks, including slab recombination.

- Added Minecraft 1.12.2 support for new surface terrain and cave floors.
- Made level 3 the default for new configurations. Existing choices are kept.
- Used Building Pieces' built-in Mineralogy steps and corners at levels 2 and 3;
  no separate Mineralogy add-on is needed.
- Kept exposed grass beneath steps and corners, including snowy transitions.
- Improved narrow ledges without recreating full-block jumps. Fitting steps and
  corners can still smooth mixed uphill and downhill edges.
- Let thin snow, grass and flowers give way to valid transitions in new terrain.
  Deeper snow, trees, crops and existing saved chunks stay untouched.
- Added the Mods Config screen, help text and separate Mineralogy test profiles.
- Updated the dependency pins to released Grass Slabs 1.1.2.112021 and
  Building Pieces 0.3.8.112021, including the Eclipse development setup.

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
