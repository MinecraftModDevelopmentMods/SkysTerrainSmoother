# Configuration

Open **Mods**, select **Sky's Terrain Smoother** and choose **Config**. You can
turn smoothing on or off and choose level 1–3. Open **Materials** for the
material switches, or **Mod support** for Mineralogy. **Back** keeps your
pending choices while you visit another page. Save with **Done** on the main
page, then restart Minecraft. Closing the main screen with Escape cancels edits.

You can also edit `config/skysterrainsmoother-common.toml` while the game or server is
stopped. On a dedicated server, edit the server's file and restart it; the
client's Config screen does not change a remote server.

| Setting | Default | Effect |
|---|---|---|
| `worldgen.enabled` | `true` | Enables combined smoothing in new Overworld chunks. |
| `worldgen.smoothingLevel` | `3` | Slabs (1), slabs and steps (2), or slabs, steps and corners (3). |
| `compat.enableMineralogy` | `true` | Includes available natural Mineralogy rock slabs when installed. |
| `materials.soil` | `true` | Grass, dirt and registered soils, including the BOP add-on's coarse dirt. |
| `materials.sandAndGravel` | `true` | Sand, red sand and gravel. |
| `materials.stone` | `true` | Stone, sandstone, terracotta and registered Mineralogy and BOP rocks. |
| `materials.snow` | `true` | Full snow blocks and powder snow. It does not disable snowy grass appearances. |
| `materials.ice` | `true` | Registered ice terrain, including packed ice. |
| `materials.other` | `true` | Add-on materials without another group. |

To keep grass and sand transitions but leave rock terrain alone, set
`materials.stone=false`. There is no need to disable Mineralogy separately.
Both the material switch and the Mineralogy support setting must allow a
Mineralogy mapping before it can be used.

All material groups start enabled, including when updating an older config.
Your existing master switch, level and Mineralogy choice are preserved. These
settings control new surface and cave smoothing only: recipes, placed pieces
and existing chunks are unchanged. A disabled rock face still contributes its
height to a neighbouring grass or sand transition.

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
