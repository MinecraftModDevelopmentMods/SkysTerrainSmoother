# Snow and frozen terrain

Snow-block and packed-ice slopes use matching Building Pieces slabs, steps and
corners. Powder snow uses separate pieces that keep its sinking, freezing and
leather-boot behaviour. All three smoothing levels work with these materials.
Existing terrain stays unchanged.

Grass transitions also pick up snow from nearby ground and exposed cold slopes.
Snowy pieces cover the visible grass beneath them without changing that grass
block in the saved world. Removing or thawing a piece restores the normal look.

## Crafting powder-snow pieces

- One powder-snow bucket makes two slabs and returns an empty bucket.
- Two slabs placed diagonally make four steps.
- Two steps side by side make four corners.
- Two corners stacked in the crafting grid make one step.
- Two steps stacked in the grid make one slab.
- Two slabs and an empty bucket make a powder-snow bucket.

Matching pieces also combine when placed together. A partial piece cannot fill
a bucket on its own; rebuild it first. Breaking a piece returns that piece.

For solid snow slabs, place two snow blocks diagonally to make four slabs.
The vanilla recipe of three snow blocks in a row still makes snow layers.
