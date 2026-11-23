"""Real-index regression: .gitignore does not protect already tracked files."""
import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from audit_repository_hygiene import category, main, summarise

class RepositoryHygieneTest(unittest.TestCase):
    def test_intentional_source_and_synthetic_inputs_are_allowed(self):
        names = ["frontend/package-lock.json", "backend/pom.xml",
            "backend/src/main/resources/db/migration/V1__workspace.sql",
            "tests/fixtures/synthetic.csv", ".env.example", "windows/build-toolchain.json"]
        self.assertEqual(0, summarise(names)["prohibited_tracked_file_count"])

    def test_private_and_generated_classes_are_blocked_without_values(self):
        names = ["frontend/node_modules/private-user-name/index.js", ".tools/jdk/bin/java.exe",
            "backend/target/classes/App.class", "backups/private-name.hfb", "runtime/private.pem",
            ".env.local", "nested/workspace.mv.db", "frontend/test-results/trace.zip",
            "release/household-finance.zip", "config.xml"]
        result = summarise(names)
        self.assertEqual(len(names), result["prohibited_tracked_file_count"])
        self.assertNotIn("private-user-name", json.dumps(result))
        self.assertNotIn("private-name", json.dumps(result))

    def test_gitignore_does_not_hide_force_tracked_outputs(self):
        with tempfile.TemporaryDirectory(prefix="gbd30-hygiene-") as folder:
            root = Path(folder).resolve()
            self.assertTrue(root.is_relative_to(Path(tempfile.gettempdir()).resolve()))
            def git(*args):
                subprocess.run(["git", "-C", str(root), *args], check=True,
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            git("init", "-q")
            (root/".gitignore").write_text(".env\nnode_modules/\n")
            (root/".env").write_text("DO_NOT_EMIT_SECRET_CONTENT\n")
            (root/"node_modules").mkdir()
            (root/"node_modules"/"private-person.js").write_text("DO_NOT_EMIT_SOURCE")
            git("add", ".gitignore")
            git("add", "-f", ".env", "node_modules/private-person.js")
            previous = Path.cwd()
            try:
                os.chdir(root)
                output = io.StringIO()
                with contextlib.redirect_stdout(output):
                    code = main()
            finally:
                os.chdir(previous)
            self.assertEqual(1, code)
            report = json.loads(output.getvalue())
            self.assertEqual(2, report["prohibited_tracked_file_count"])
            self.assertNotIn("DO_NOT_EMIT", output.getvalue())
            self.assertNotIn("private-person", output.getvalue())

if __name__ == "__main__":
    unittest.main()
