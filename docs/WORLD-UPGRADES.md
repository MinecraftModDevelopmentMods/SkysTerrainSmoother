# Existing worlds

Back up a world before changing its mods. Install the required content mods on
both the client and server before loading it.

Terrain Smoother indexes saved Overworld region entries before generation
starts. It skips those chunks, including chunks saved before decoration was
completed. There is no retrogen option in this beta. Travel to previously
ungenerated terrain to see the effect.

Grass Slabs and Building Pieces continue to handle their own supported legacy
content. Terrain Smoother neither expands that compatibility claim nor makes
removing an unrelated mod safe. Keep the BOP add-on installed when a world
contains its pieces. Other biome mods need a compatible material add-on before
their terrain can be smoothed.

Turning smoothing off does not remove pieces already placed. They remain
ordinary blocks owned by the relevant content mod.

## Upgrading to Minecraft 1.21.1

Update Grass Slabs, Building Pieces and Terrain Smoother together. Keep
your configuration and world data so your settings and migration history
carry over. Sand slabs keep their colour, orientation and waterlogging.
Travel to new chunks to see smoothing on newly generated terrain.

You can also move a Minecraft 1.21.1 Forge save to NeoForge. First check
that every other mod in the world has a compatible NeoForge build. Use a
copy of the save and install the NeoForge versions of all three Sky mods.

## Upgrading from Minecraft 1.18.2

Update Terrain Smoother, Grass Slabs and Building Pieces together. Keep your
configuration and world data when you move the save. Sand slabs retain their
colour, slab half and waterlogging. Smoothing applies to newly generated chunks.

## Upgrading from Minecraft 1.17.1

Update the three mods together and keep your configuration and world data.
Saved pieces, inventories and migration history are retained. The taller world
range also applies to newly generated cave floors; existing chunks stay unchanged.

## Upgrading from Minecraft 1.16.5

Update Grass Slabs, Building Pieces and Terrain Smoother together. Keep the
configuration and world data files to retain your choices and migration history.
Saved sand slabs keep their colour, upper or lower half, water and item data.
Existing chunks stay unchanged; smoothing still applies only to new terrain.

## Upgrading from Minecraft 1.14.4

Update Grass Slabs, Building Pieces and Terrain Smoother together. Existing
pieces keep their named states, including sand colour, slab halves and water.
Keep your configuration files to retain the smoothing level and other choices.
Saved chunks stay unchanged; smoothing still applies only to new terrain.

## Upgrading from Minecraft 1.12.2

Use a backup and keep both required content mods installed. Sand slabs keep their
colour and upper/lower placement. Red sand now has its own block and item ID,
following Minecraft's flattened block format; saved red sand slabs and inventory
stacks are converted automatically, including unopened chunks. Custom item data
and stack counts are kept. An unsupported saved sand value stops the upgrade
with a report rather than replacing it with ordinary sand.

This conversion does not smooth old terrain. Existing pieces remain in place,
and the saved-chunk index excludes previously generated chunks from new smoothing.
