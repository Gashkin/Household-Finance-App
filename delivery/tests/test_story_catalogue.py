"""Check declared source evidence against the checkout exercised by Required CI."""
import json
from pathlib import Path
import unittest

from delivery.coordinator import validate_definition


ROOT = Path(__file__).resolve().parents[2]


class StoryCatalogueTests(unittest.TestCase):
    def test_declared_artifacts_match_the_checkout(self):
        definitions = sorted((ROOT / '.delivery/stories').glob('GBD-*.json'))
        self.assertTrue(definitions, 'No delivery story definitions found')
        for definition in definitions:
            with self.subTest(definition=definition.name):
                story = validate_definition(json.loads(definition.read_text(encoding='utf-8')))
                self.assertEqual(definition.stem, story['work_id'])
                for artifact in story['artifacts']:
                    with self.subTest(path=artifact['path']):
                        content = (ROOT / artifact['path']).read_text(encoding='utf-8')
                        missing = [marker for marker in artifact['contains'] if marker not in content]
                        self.assertEqual(missing, [], 'Declared markers missing from this checkout')
