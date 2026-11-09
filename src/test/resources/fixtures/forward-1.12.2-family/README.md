# Minecraft 1.12.2 family upgrade fixture

This world was saved by the accepted Grass Slabs 1.1.2, Building Pieces 0.3.8
and Terrain Smoother 0.1.0 jars. Exact commits and jar hashes are recorded in
source-artifacts.json. It contains 3,405 placed pieces and matching chest stacks,
four dropped sand-slab stacks, an item frame and one saved player with main,
ender and nested inventories. The shovel has a name, damage, an enchantment and
custom data. Snow neighbours and populated schema-1 world state are retained.

Tests upgrade disposable copies only, then reload the saved result. Never run
this source in place. source-file-sha256.json records the original saved files;
the archive omits only the session lock. No source mods or textures are bundled.
