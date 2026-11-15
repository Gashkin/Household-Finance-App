import json
from pathlib import Path
import subprocess
import tempfile
import unittest


class ExposureAuditTest(unittest.TestCase):
    def test_removed_sensitive_history_is_counted_without_printing_paths_or_contents(self):
        audit = Path(__file__).with_name('audit_history_exposure.py').resolve()
        with tempfile.TemporaryDirectory() as folder:
            def git(*args):
                return subprocess.check_output(['git', '-C', folder, *args], stderr=subprocess.DEVNULL)
            git('init')
            git('config', 'user.name', 'Synthetic test')
            git('config', 'user.email', 'synthetic@example.invalid')
            private = Path(folder, 'private-person-synthetic.xlsx')
            private.write_text('synthetic-never-publish-content')
            git('add', '.')
            git('commit', '-m', 'Synthetic history')
            git('rm', private.name)
            git('commit', '-m', 'Synthetic cleanup')
            result = subprocess.check_output(['python', str(audit)], cwd=folder).decode()
            data = json.loads(result)
            self.assertEqual(2, data['reachable_commits'])
            self.assertEqual({'workbook': 1}, data['history_categories_unique_paths'])
            self.assertFalse(data['shallow'])
            self.assertEqual({}, data['pattern_candidate_blob_counts'])
            self.assertNotIn(private.name, result)
            self.assertNotIn('synthetic-never-publish-content', result)


if __name__ == '__main__':
    unittest.main()
