from __future__ import annotations

import argparse
from datetime import datetime
import re
import sys
from pathlib import Path

REQUIRED_KEYS = ["type", "title", "description", "resource", "tags", "timestamp"]
MARKDOWN_GLOB = "**/*.md"
ALLOWED_TYPES = {
    "KnowledgeBundle",
    "ConceptIndex",
    "DomainEntity",
    "Workflow",
    "Rule",
    "ArchitectureDecision",
    "WorkItem",
    "API",
    "Metric",
    "MigrationMap",
    "Runbook",
    "Service",
    "Dataset",
}


def is_valid_iso8601(timestamp: str) -> bool:
    value = timestamp.strip()
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    try:
        datetime.fromisoformat(value)
    except ValueError:
        return False
    return True


def is_valid_tags_field(tags_value: str) -> bool:
    text = tags_value.strip()
    if not (text.startswith("[") and text.endswith("]")):
        return False
    inner = text[1:-1].strip()
    return bool(inner)


def resolve_resource_path(resource: str, repo_root: Path) -> Path | None:
    value = resource.strip()
    if value.startswith("http://") or value.startswith("https://"):
        return None
    return (repo_root / value).resolve()


def split_frontmatter(text: str) -> tuple[dict[str, str] | None, str]:
    if not text.startswith("---\n"):
        return None, text

    end_marker = "\n---\n"
    end_idx = text.find(end_marker, 4)
    if end_idx == -1:
        return None, text

    raw = text[4:end_idx].strip("\n")
    body = text[end_idx + len(end_marker):]

    parsed: dict[str, str] = {}
    for line in raw.splitlines():
        if not line.strip() or line.strip().startswith("#"):
            continue
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        parsed[key.strip()] = value.strip()

    return parsed, body


def extract_markdown_links(text: str) -> list[str]:
    pattern = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
    return [match.group(1) for match in pattern.finditer(text)]


def validate_okf(root: Path, check_links: bool) -> int:
    files = sorted(root.glob(MARKDOWN_GLOB))
    if not files:
        print(f"No markdown files found under {root}")
        return 1

    repo_root = root.parent.parent.resolve()

    errors: list[str] = []
    warnings: list[str] = []

    for file_path in files:
        text = file_path.read_text(encoding="utf-8")
        frontmatter, body = split_frontmatter(text)

        rel = file_path.relative_to(root.parent)

        if frontmatter is None:
            errors.append(f"{rel}: missing or malformed YAML frontmatter")
            continue

        for key in REQUIRED_KEYS:
            if key not in frontmatter or not frontmatter[key]:
                errors.append(f"{rel}: missing required frontmatter key '{key}'")

        if "type" in frontmatter and frontmatter["type"] not in ALLOWED_TYPES:
            errors.append(
                f"{rel}: invalid type '{frontmatter['type']}' (allowed: {', '.join(sorted(ALLOWED_TYPES))})"
            )

        if "timestamp" in frontmatter and not is_valid_iso8601(frontmatter["timestamp"]):
            errors.append(f"{rel}: invalid timestamp '{frontmatter['timestamp']}' (expected ISO-8601)")

        if "tags" in frontmatter and not is_valid_tags_field(frontmatter["tags"]):
            errors.append(f"{rel}: invalid tags '{frontmatter['tags']}' (expected non-empty [a, b] style)")

        if "resource" in frontmatter:
            resource_target = resolve_resource_path(frontmatter["resource"], repo_root)
            if resource_target is not None and not resource_target.exists():
                errors.append(
                    f"{rel}: resource path does not exist '{frontmatter['resource']}'"
                )

        if check_links:
            links = extract_markdown_links(body)
            for link in links:
                if link.startswith("http://") or link.startswith("https://"):
                    continue
                target = (file_path.parent / link).resolve()
                if not target.exists():
                    warnings.append(f"{rel}: broken local link '{link}'")

    print("OKF validation summary")
    print(f"- Root: {root}")
    print(f"- Files scanned: {len(files)}")
    print(f"- Errors: {len(errors)}")
    print(f"- Warnings: {len(warnings)}")

    if errors:
        print("\nErrors:")
        for error in errors:
            print(f"- {error}")

    if warnings:
        print("\nWarnings:")
        for warning in warnings:
            print(f"- {warning}")

    return 1 if errors else 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate OKF markdown files and required frontmatter.")
    parser.add_argument(
        "--root",
        default="knowledge/okf",
        help="Path to OKF root directory (default: knowledge/okf)",
    )
    parser.add_argument(
        "--no-link-check",
        action="store_true",
        help="Disable local markdown link checks",
    )
    args = parser.parse_args()

    root = Path(args.root).resolve()
    if not root.exists() or not root.is_dir():
        print(f"Invalid OKF root: {root}")
        return 1

    return validate_okf(root, check_links=not args.no_link_check)


if __name__ == "__main__":
    sys.exit(main())
