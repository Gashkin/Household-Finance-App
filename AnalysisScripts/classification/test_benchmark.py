import contextlib
import io
import json
import os
import tempfile
import unittest
from unittest.mock import patch
import benchmark


class ScorerContractTest(unittest.TestCase):
    def test_both_transports_score_the_production_contract(self):
        cases = json.loads((benchmark.ROOT / "contract-cases.json").read_text())
        for provider in ("local", "openai"):
            for case in cases:
                with self.subTest(provider=provider, case=case["name"]), tempfile.TemporaryDirectory() as tmp:
                    wire = (case["wire"] + " " * case.get("padding", 0)).encode()
                    capture = io.StringIO()
                    with patch.dict(os.environ, {"OPENAI_API_KEY":"mock", "FINANCE_INFERENCE_KEY":"mock",
                                                 "LOCALAPPDATA":tmp}), patch("urllib.request.build_opener") as build:
                        build.return_value.open.side_effect = lambda *args, **kwargs: io.BytesIO(wire)
                        with contextlib.redirect_stdout(capture):
                            benchmark.evaluate(provider)
                    result = json.loads(capture.getvalue())
                    self.assertEqual(8, result["cases"])
                    for counts in result["scores"].values():
                        self.assertEqual(8, counts["abstained" if case["accepted"] else "invalid"])
                        self.assertEqual(0, counts.get("correct", 0))
                    self.assertEqual(8, build.return_value.open.call_count)
