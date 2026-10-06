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
