"""Keep powder-snow models, recipe identities and bucket accounting explicit."""
import json
from pathlib import Path
import unittest
from generate_powder_snow import ROOT, resources


class PowderSnowResources(unittest.TestCase):
    def test_all_generated_resources_match_their_source(self):
        expected, _ = resources()
        self.assertEqual(42, len(expected))
        for relative, data in expected.items():
            path = ROOT / 'src/main/resources' / relative
            self.assertEqual(data, json.loads(path.read_text(encoding='utf-8')), relative)

    def test_only_the_three_flat_piece_ids_are_generated(self):
        expected, _ = resources()
        blockstates = {Path(path).stem for path in expected if '/blockstates/' in path}
        self.assertEqual({'powder_snow_slab', 'powder_snow_step', 'powder_snow_corner'}, blockstates)
        for path, model in expected.items():
            if '/models/block/' in path:
                for element in model['elements']:
                    self.assertEqual(8, element['to'][1] - element['from'][1])
                    self.assertTrue(all('cullface' not in face for face in element['faces'].values()))

    def test_all_six_recipe_unlocks_remain_in_the_manifest(self):
        _, expected = resources()
        actual = json.loads((ROOT / 'src/main/recipe-book/recipes.json').read_text(encoding='utf-8'))
        actual = [row for row in actual if row['recipe'].startswith('skysterrainsmoother:powder_snow_')]
        self.assertEqual(6, len(actual))
        self.assertEqual(expected, actual)

    def test_shovels_keep_their_native_advantage_on_all_powder_pieces(self):
        tag = json.loads((ROOT / 'src/main/resources/data/minecraft/tags/block/mineable/shovel.json')
                         .read_text(encoding='utf-8'))
        self.assertFalse(tag['replace'])
        for shape in ('slab', 'step', 'corner'):
            self.assertIn('skysterrainsmoother:powder_snow_' + shape, tag['values'])


if __name__ == '__main__':
    unittest.main()
