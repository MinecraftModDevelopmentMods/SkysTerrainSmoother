"""Build the small powder-snow resource set from its horizontal footprints."""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MOD = 'skysterrainsmoother'
DIRECTIONS = ('north', 'east', 'south', 'west')


def resources():
    files = {}
    for kind in ('slab', 'step', 'corner'):
        name = 'powder_snow_' + kind
        variants = {}
        for half in ('bottom', 'top'):
            for direction, facing in enumerate(DIRECTIONS):
                mask = 15 if kind == 'slab' else (3, 6, 12, 9)[direction] if kind == 'step' else 1 << direction
                corners = [(0 if cell in (0, 3) else 8, 0 if cell < 2 else 8)
                           for cell in range(4) if mask & 1 << cell]
                bottom = 0 if half == 'bottom' else 8
                model = name + '_' + half + '_' + facing
                files['assets/' + MOD + '/models/block/' + model + '.json'] = {
                    'parent': 'minecraft:block/block',
                    'textures': {'particle': 'minecraft:block/powder_snow', 'snow': 'minecraft:block/powder_snow'},
                    'elements': [{'from': [min(x for x, z in corners), bottom, min(z for x, z in corners)],
                                  'to': [max(x for x, z in corners) + 8, bottom + 8, max(z for x, z in corners) + 8],
                                  'faces': {side: {'texture': '#snow'} for side in
                                            ('down', 'up', 'north', 'south', 'west', 'east')}}]}
                variants['facing=' + facing + ',half=' + half] = {'model': MOD + ':block/' + model}
        files['assets/' + MOD + '/blockstates/' + name + '.json'] = {'variants': variants}
        files['assets/' + MOD + '/models/item/' + name + '.json'] = {
            'parent': MOD + ':block/' + name + '_bottom_north'}
        files['assets/' + MOD + '/items/' + name + '.json'] = {
            'model': {'type': 'minecraft:model', 'model': MOD + ':item/' + name}}
        files['data/' + MOD + '/loot_table/blocks/' + name + '.json'] = {
            'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item',
            'name': MOD + ':' + name}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}

    slab, step, corner = [MOD + ':powder_snow_' + kind for kind in ('slab', 'step', 'corner')]
    manifest = []

    def recipe(name, body, output, count, inputs):
        body.update({'category': 'misc' if output.endswith('_bucket') else 'building',
                     'group': 'skys/result/' + output.replace(':', '/'),
                     'result': {'id': output, 'count': count}})
        files['data/' + MOD + '/recipe/' + name + '.json'] = body
        manifest.append({'recipe': MOD + ':' + name, 'owner': MOD,
                         'ingredients': [{'items': [item]} for item in inputs]})

    recipe('powder_snow_slab', {'type': 'minecraft:crafting_shapeless',
           'ingredients': [{'item': 'minecraft:powder_snow_bucket'}]}, slab, 2,
           ['minecraft:powder_snow_bucket'])
    files['data/' + MOD + '/recipe/powder_snow_slab.json'] = {
        'type': MOD + ':powder_snow_cutting'}
    for name, pattern, ingredient, output, count in (
            ('powder_snow_step', ['X ', ' X'], slab, step, 4),
            ('powder_snow_corner', ['XX'], step, corner, 4),
            ('powder_snow_step_from_corners', ['X', 'X'], corner, step, 1),
            ('powder_snow_slab_from_steps', ['X', 'X'], step, slab, 1)):
        recipe(name, {'type': 'minecraft:crafting_shaped', 'pattern': pattern,
                     'key': {'X': {'item': ingredient}}}, output, count, [ingredient])
    recipe('powder_snow_from_slabs', {'type': 'minecraft:crafting_shapeless',
           'ingredients': [{'item': slab}, {'item': slab}, {'item': 'minecraft:bucket'}]},
           'minecraft:powder_snow_bucket', 1, [slab])
    # Ingredients use identifiers or tags; object-form ingredients are no longer accepted.
    for relative, body in files.items():
        if '/recipe/' in relative:
            if 'key' in body:
                body['key'] = {key: value['item'] for key, value in body['key'].items()}
            if 'ingredients' in body:
                body['ingredients'] = [value['item'] for value in body['ingredients']]
    return files, manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true')
    options = parser.parse_args()
    files, manifest = resources()
    target = ROOT / 'src/main/resources'
    changed = []
    for relative, body in files.items():
        path = target / relative
        expected = json.dumps(body, indent=2) + '\n'
        if not path.is_file() or path.read_text(encoding='utf-8') != expected:
            changed.append(relative)
            if options.write:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(expected, encoding='utf-8', newline='\n')
    path = ROOT / 'src/main/recipe-book/recipes.json'
    current = json.loads(path.read_text(encoding='utf-8'))
    merged = [row for row in current if not row['recipe'].startswith(MOD + ':powder_snow_')] + manifest
    if current != merged:
        changed.append('recipe-book manifest')
        if options.write:
            path.write_text(json.dumps(merged, indent=2) + '\n', encoding='utf-8', newline='\n')
    print('Powder snow resources:', len(files), 'files,', len(changed), 'changes')
    if changed and not options.write:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
