"""Reject tracked local outputs/private-file classes; never print file paths or contents."""
from __future__ import annotations
from collections import Counter
import json
from pathlib import PurePosixPath
import subprocess
import sys

DIRECTORIES = {
    "node_modules": "dependencies",
    ".venv": "dependencies",
    "__pycache__": "build_cache",
    "target": "build_output",
    "dist": "build_output",
    ".tools": "local_toolchains",
    ".worktrees": "local_worktrees",
    "test-results": "test_evidence",
    "playwright-report": "test_evidence",
}
PRIVATE_SUFFIXES = (".pem", ".key", ".p12", ".pfx", ".jks", ".hfb", ".db")
ARCHIVE_SUFFIXES = (".jar", ".war", ".zip", ".tar.gz", ".rar")
EXAMPLE_ENV = {".env.example"}

def category(name: str) -> str | None:
    path = PurePosixPath(name.lower())
    parts = path.parts
    for segment in parts[:-1]:
        if segment in DIRECTORIES:
            return DIRECTORIES[segment]
    if parts and parts[0] in {"release", "backups", "secrets", "data"}:
        return "runtime_or_release"
    if name.lower().startswith("backend/data/"):
        return "runtime_data"
    if path.name.startswith(".env") and path.name not in EXAMPLE_ENV:
        return "private_configuration"
    if path.name in {"config.xml", "bank-connection.bin", "bank-connection.bin.initialized"}:
        return "private_configuration"
    if path.name.endswith(PRIVATE_SUFFIXES):
        return "private_material"
    if path.name.endswith(ARCHIVE_SUFFIXES):
        return "binary_artifacts"
    if path.name.endswith((".class", ".pyc", ".pyo", ".tsbuildinfo", ".log")):
        return "build_cache"
    if name.lower() in {"frontend/vite.config.js", "frontend/vite.config.d.ts", "delivery-receipt.json"}:
        return "generated_configuration_or_receipt"
    return None

def summarise(names: list[str]) -> dict:
    counts = Counter(value for name in names if (value := category(name)) is not None)
    return {
        "tracked_file_count": len(names),
        "prohibited_tracked_file_count": sum(counts.values()),
        "category_counts": dict(sorted(counts.items())),
        "paths_or_contents_emitted": False,
        "scope": "Tracked Git index only; not a secret scan, history assessment or package verification.",
    }

def main() -> int:
    try:
        result = subprocess.run(["git", "ls-files", "-z"], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=True)
        names = result.stdout.decode("utf-8", errors="surrogateescape").rstrip("\0").split("\0") if result.stdout else []
        report = summarise(names)
    except (OSError, subprocess.CalledProcessError):
        print(json.dumps({"error": "Tracked-file inventory unavailable; hygiene not verified."}))
        return 2
    print(json.dumps(report, indent=2))
    return 1 if report["prohibited_tracked_file_count"] else 0

if __name__ == "__main__":
    sys.exit(main())
