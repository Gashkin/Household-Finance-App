from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from uuid import uuid4


@dataclass
class Operation:
    component: str
    action: str
    identifier: str


def normalize(value: str) -> str:
    return "".join(ch for ch in value.lower() if ch.isalnum())


def http_json(method: str, url: str, payload: dict[str, Any] | None = None, timeout: float = 15.0) -> Any:
    data = None
    headers = {"Accept": "application/json"}
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"

    request = urllib.request.Request(url=url, method=method, headers=headers, data=data)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            content = response.read().decode("utf-8")
            if not content:
                return None
            return json.loads(content)
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {url} failed with {error.code}: {body}") from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"{method} {url} failed: {error.reason}") from error


def planner_url(base_url: str, suffix: str) -> str:
    return f"{base_url.rstrip('/')}/planner{suffix}"


def load_extract(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise RuntimeError(f"Extract file not found: {path}")

    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise RuntimeError("Invalid extract payload. Expected a JSON object.")
    return payload


def build_desired_accounts(payload: dict[str, Any]) -> list[dict[str, Any]]:
    raw = payload.get("accounts", [])
    desired: list[dict[str, Any]] = []
    for entry in raw:
        if not isinstance(entry, dict):
            continue
        name = str(entry.get("name", "")).strip()
        if not name:
            continue
        desired.append(
            {
                "name": name,
                "type": str(entry.get("accountType", "Bank")).strip() or "Bank",
                "balance": float(entry.get("balance", 0.0)),
            }
        )
    return desired


def build_desired_categories(payload: dict[str, Any]) -> list[dict[str, Any]]:
    raw = payload.get("categories", [])
    desired: list[dict[str, Any]] = []
    for entry in raw:
        if not isinstance(entry, dict):
            continue
        name = str(entry.get("name", "")).strip()
        budget = str(entry.get("budget", "")).strip()
        if not name or not budget:
            continue
        desired.append({"name": name, "budget": budget})
    return desired


def build_desired_commitments(payload: dict[str, Any]) -> list[dict[str, Any]]:
    raw = payload.get("commitments", [])
    desired: list[dict[str, Any]] = []
    for entry in raw:
        if not isinstance(entry, dict):
            continue
        title = str(entry.get("title", "")).strip()
        due_date = str(entry.get("dueDate", "")).strip()
        if not title or not due_date:
            continue
        desired.append(
            {
                "title": title,
                "amount": float(entry.get("amount", 0.0)),
                "dueDate": due_date,
            }
        )
    return desired


def get_current_planner_state(base_url: str, timeout: float) -> dict[str, Any]:
    state = http_json("GET", planner_url(base_url, ""), timeout=timeout)
    if not isinstance(state, dict):
        raise RuntimeError("Unexpected planner response from backend.")
    return state


def index_by_normalized_name(rows: list[dict[str, Any]], key: str) -> dict[str, dict[str, Any]]:
    indexed: dict[str, dict[str, Any]] = {}
    for row in rows:
        value = str(row.get(key, "")).strip()
        if value:
            indexed[normalize(value)] = row
    return indexed


def generate_id(prefix: str) -> str:
    return f"WL-{prefix}-{uuid4().hex[:8].upper()}"


def apply_replace(
    base_url: str,
    timeout: float,
    desired_accounts: list[dict[str, Any]],
    desired_categories: list[dict[str, Any]],
    desired_commitments: list[dict[str, Any]],
    include_categories: bool,
    dry_run: bool,
) -> list[Operation]:
    operations: list[Operation] = []
    current = get_current_planner_state(base_url, timeout)

    for account in current.get("accounts", []):
        account_id = str(account.get("id", "")).strip()
        if account_id:
            operations.append(Operation("accounts", "delete", account_id))
            if not dry_run:
                http_json("DELETE", planner_url(base_url, f"/accounts/{account_id}"), timeout=timeout)

    for commitment in current.get("commitments", []):
        commitment_id = str(commitment.get("id", "")).strip()
        if commitment_id:
            operations.append(Operation("commitments", "delete", commitment_id))
            if not dry_run:
                http_json("DELETE", planner_url(base_url, f"/commitments/{commitment_id}"), timeout=timeout)

    if include_categories:
        for category in current.get("categories", []):
            category_id = str(category.get("id", "")).strip()
            if category_id:
                operations.append(Operation("categories", "delete", category_id))
                if not dry_run:
                    http_json("DELETE", planner_url(base_url, f"/categories/{category_id}"), timeout=timeout)

    for account in desired_accounts:
        new_id = generate_id("A")
        payload = {"id": new_id, "name": account["name"], "type": account["type"], "balance": account["balance"]}
        operations.append(Operation("accounts", "create", new_id))
        if not dry_run:
            http_json("POST", planner_url(base_url, "/accounts"), payload=payload, timeout=timeout)

    if include_categories:
        for category in desired_categories:
            new_id = generate_id("C")
            payload = {"id": new_id, "name": category["name"], "budget": category["budget"]}
            operations.append(Operation("categories", "create", new_id))
            if not dry_run:
                http_json("POST", planner_url(base_url, "/categories"), payload=payload, timeout=timeout)

    for commitment in desired_commitments:
        new_id = generate_id("M")
        payload = {
            "id": new_id,
            "title": commitment["title"],
            "amount": commitment["amount"],
            "dueDate": commitment["dueDate"],
        }
        operations.append(Operation("commitments", "create", new_id))
        if not dry_run:
            http_json("POST", planner_url(base_url, "/commitments"), payload=payload, timeout=timeout)

    return operations


def apply_merge(
    base_url: str,
    timeout: float,
    desired_accounts: list[dict[str, Any]],
    desired_categories: list[dict[str, Any]],
    desired_commitments: list[dict[str, Any]],
    include_categories: bool,
    dry_run: bool,
) -> list[Operation]:
    operations: list[Operation] = []
    current = get_current_planner_state(base_url, timeout)

    existing_accounts = index_by_normalized_name(current.get("accounts", []), "name")
    for account in desired_accounts:
        key = normalize(account["name"])
        existing = existing_accounts.get(key)
        if existing:
            account_id = str(existing.get("id", "")).strip()
            payload = {"id": account_id, "name": account["name"], "type": account["type"], "balance": account["balance"]}
            operations.append(Operation("accounts", "update", account_id))
            if not dry_run:
                http_json("PUT", planner_url(base_url, f"/accounts/{account_id}"), payload=payload, timeout=timeout)
        else:
            new_id = generate_id("A")
            payload = {"id": new_id, "name": account["name"], "type": account["type"], "balance": account["balance"]}
            operations.append(Operation("accounts", "create", new_id))
            if not dry_run:
                http_json("POST", planner_url(base_url, "/accounts"), payload=payload, timeout=timeout)

    if include_categories:
        existing_categories = index_by_normalized_name(current.get("categories", []), "name")
        for category in desired_categories:
            key = normalize(category["name"])
            existing = existing_categories.get(key)
            if existing:
                category_id = str(existing.get("id", "")).strip()
                payload = {"id": category_id, "name": category["name"], "budget": category["budget"]}
                operations.append(Operation("categories", "update", category_id))
                if not dry_run:
                    http_json("PUT", planner_url(base_url, f"/categories/{category_id}"), payload=payload, timeout=timeout)
            else:
                new_id = generate_id("C")
                payload = {"id": new_id, "name": category["name"], "budget": category["budget"]}
                operations.append(Operation("categories", "create", new_id))
                if not dry_run:
                    http_json("POST", planner_url(base_url, "/categories"), payload=payload, timeout=timeout)

    existing_commitments = index_by_normalized_name(current.get("commitments", []), "title")
    for commitment in desired_commitments:
        key = normalize(commitment["title"])
        existing = existing_commitments.get(key)
        if existing:
            commitment_id = str(existing.get("id", "")).strip()
            payload = {
                "id": commitment_id,
                "title": commitment["title"],
                "amount": commitment["amount"],
                "dueDate": commitment["dueDate"],
            }
            operations.append(Operation("commitments", "update", commitment_id))
            if not dry_run:
                http_json("PUT", planner_url(base_url, f"/commitments/{commitment_id}"), payload=payload, timeout=timeout)
        else:
            new_id = generate_id("M")
            payload = {
                "id": new_id,
                "title": commitment["title"],
                "amount": commitment["amount"],
                "dueDate": commitment["dueDate"],
            }
            operations.append(Operation("commitments", "create", new_id))
            if not dry_run:
                http_json("POST", planner_url(base_url, "/commitments"), payload=payload, timeout=timeout)

    return operations


def summarize_operations(operations: list[Operation]) -> dict[str, dict[str, int]]:
    summary: dict[str, dict[str, int]] = {
        "accounts": {"create": 0, "update": 0, "delete": 0},
        "categories": {"create": 0, "update": 0, "delete": 0},
        "commitments": {"create": 0, "update": 0, "delete": 0},
    }

    for operation in operations:
        if operation.component in summary and operation.action in summary[operation.component]:
            summary[operation.component][operation.action] += 1
    return summary


def main() -> None:
    parser = argparse.ArgumentParser(description="Load extracted workbook data into planner backend.")
    parser.add_argument(
        "--input",
        default="AnalysisScripts/output/loader-extract.json",
        help="Path to extracted JSON payload.",
    )
    parser.add_argument(
        "--base-url",
        default="http://localhost:8080",
        help="Backend base URL.",
    )
    parser.add_argument(
        "--mode",
        choices=["merge", "replace"],
        default="merge",
        help="Load mode.",
    )
    parser.add_argument(
        "--include-categories",
        action="store_true",
        help="Load categories from extract payload if present.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Plan operations but do not write to backend.",
    )
    parser.add_argument(
        "--report",
        default="AnalysisScripts/output/load-report.json",
        help="Path to write load report JSON.",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=15.0,
        help="HTTP request timeout in seconds.",
    )
    args = parser.parse_args()

    payload = load_extract(Path(args.input))
    accounts = build_desired_accounts(payload)
    categories = build_desired_categories(payload)
    commitments = build_desired_commitments(payload)

    if not accounts and not commitments and (not args.include_categories or not categories):
        raise SystemExit("No loadable accounts/commitments/categories found in extract payload.")

    if args.mode == "replace":
        operations = apply_replace(
            args.base_url,
            args.timeout,
            accounts,
            categories,
            commitments,
            args.include_categories,
            args.dry_run,
        )
    else:
        operations = apply_merge(
            args.base_url,
            args.timeout,
            accounts,
            categories,
            commitments,
            args.include_categories,
            args.dry_run,
        )

    operation_summary = summarize_operations(operations)
    report = {
        "mode": args.mode,
        "dryRun": args.dry_run,
        "input": args.input,
        "baseUrl": args.base_url,
        "desired": {
            "accounts": len(accounts),
            "categories": len(categories) if args.include_categories else 0,
            "commitments": len(commitments),
        },
        "operations": [operation.__dict__ for operation in operations],
        "operationSummary": operation_summary,
    }

    report_path = Path(args.report)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")

    print("Loader execution complete")
    print(f"- Mode: {args.mode}")
    print(f"- Dry run: {args.dry_run}")
    print(f"- Accounts desired: {len(accounts)}")
    print(f"- Categories desired: {len(categories) if args.include_categories else 0}")
    print(f"- Commitments desired: {len(commitments)}")
    print(f"- Account ops: {operation_summary['accounts']}")
    print(f"- Category ops: {operation_summary['categories']}")
    print(f"- Commitment ops: {operation_summary['commitments']}")
    print(f"- Report: {report_path}")


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1) from error
