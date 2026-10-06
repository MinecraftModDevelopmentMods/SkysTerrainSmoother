# Configuration

Edit `config/skysterrainsmoother.cfg` while the game or server is stopped.
All settings require a restart.

| Setting | Default | Effect |
|---|---|---|
| `worldgen.enabled` | `true` | Enables combined smoothing in new Overworld chunks. |
| `worldgen.smoothingLevel` | `1` | Slabs (1), slabs and steps (2), or slabs, steps and corners (3). |
| `compat.enableMineralogy` | `true` | Includes available natural Mineralogy rock slabs when installed. |

Levels outside 1–3 are constrained to that range. Missing finer shapes fall
back to slabs; disabled Mineralogy slabs are skipped. This mod adds no steps or
corners to Mineralogy.

`worldgen.generateGrassSlabs` in Grass Slabs remains the grass material switch.
Turning it off does not disable stone smoothing. When Terrain Smoother is
installed but its master switch is off, Grass Slabs does not run a separate
pass. Removing Terrain Smoother restores the standalone Grass Slabs pass.

Grass Slabs retains its safe arbitration with BuildingBricks' grass generator.
If arbitration cannot complete, grass smoothing is suppressed for that session
rather than allowing both generators to run.

The content mods' legacy replacement options are independent. Terrain Smoother
does not enable them, replace saved pieces or alter migration reports.
