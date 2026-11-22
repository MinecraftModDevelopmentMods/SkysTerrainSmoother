# Configuration

Open **Mods**, select **Sky's Terrain Smoother** and choose **Config**. You can
turn smoothing on or off, choose level 1–3, select material groups and
enable or disable Mineralogy support. Material and compatibility settings have
their own pages. Back keeps your pending choices; Done saves them. Escape
from the main page discards unsaved edits. Save with **Done**, then restart Minecraft to apply your choices.

You can also edit `config/skysterrainsmoother-common.toml` while the game or server is
stopped. On a dedicated server, edit the server's file and restart it; the
client's Config screen does not change a remote server.

| Setting | Default | Effect |
|---|---|---|
| `worldgen.enabled` | `true` | Enables combined smoothing in new Overworld chunks. |
| `worldgen.smoothingLevel` | `3` | Slabs (1), slabs and steps (2), or slabs, steps and corners (3). |
| `compat.enableMineralogy` | `true` | Includes available natural Mineralogy rock pieces when installed. |
| `materials.soil` | `true` | Grass and dirt materials. |
| `materials.sandAndGravel` | `true` | Sand, red sand and gravel. |
| `materials.stone` | `true` | Stone, sandstone, terracotta and registered modded rocks. |
| `materials.snow` | `true` | Snow-block and powder-snow terrain. |
| `materials.ice` | `true` | Registered ice terrain, including packed ice. |
| `materials.other` | `true` | Add-on materials outside the other groups. |

Levels outside 1–3 are constrained to that range.
Existing level choices are preserved; the new default applies only when the
setting has not been saved yet.

Missing finer shapes fall back to slabs where they fit without raising a
downhill edge. Otherwise that placement is left alone.
Disabled Mineralogy slabs are skipped. Building Pieces includes the matching
rock steps and corners when Mineralogy is installed, so levels 2 and 3 use
them automatically. No separate Mineralogy add-on is needed.

The same level applies to dry surface slopes and cave floors in new chunks.
There is no retrogen: saved caves are unchanged. Low passages, ceilings and
underwater floors are not smoothed. Ores are not smoothing materials, but an
ore vein generated beneath an existing piece keeps that piece in place.

`worldgen.generateGrassSlabs` in Grass Slabs remains the grass material switch.
Turning it off does not disable stone smoothing. When Terrain Smoother is
installed but its master switch is off, Grass Slabs does not run a separate
pass. Removing Terrain Smoother restores the standalone Grass Slabs pass.

Grass Slabs retains its safe arbitration with BuildingBricks' grass generator.
If arbitration cannot complete, grass smoothing is suppressed for that session
rather than allowing both generators to run.

The content mods' legacy replacement options are independent. Terrain Smoother
does not enable them, replace saved pieces or alter migration reports.


Material choices apply to both surface slopes and cave floors, including add-on
mappings. A disabled material still contributes its height when the engine
checks an adjacent enabled material. Existing choices and saved terrain are
preserved when new settings are added.
