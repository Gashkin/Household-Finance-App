"""Query OSV for an explicit Maven dependency:list inventory, never application data.

Usage: python scan_maven_advisories.py inventory.txt report.json
Exit 0: complete query with no matches; 1: advisory matches; 2: invalid/incomplete scan.
Matches require advisory/reachability review; zero matches are not a security audit.
"""
import datetime
import hashlib
import json
import pathlib
import re
import sys
import urllib.request


def main():
    source, destination = map(pathlib.Path, sys.argv[1:])
    raw = source.read_bytes()
    dependencies = []
    for line in raw.decode("utf-8").splitlines():
        match = re.search(r"([\w.-]+):([\w.-]+):jar:(?:[^:\s]+:)?([^:\s]+):(compile|runtime|test|provided)(?:\s|$)", line)
        if match:
            group, artifact, version, scope = match.groups()
            dependencies.append({"name": f"{group}:{artifact}", "version": version, "scope": scope})
    if not dependencies or len(dependencies) > 1000:
        raise ValueError("Expected a bounded, nonempty Maven dependency:list inventory")
    results = []
    for start in range(0, len(dependencies), 100):
        batch = dependencies[start:start + 100]
        query = {"queries": [{"package": {"name": d["name"], "ecosystem": "Maven"}, "version": d["version"]} for d in batch]}
        request = urllib.request.Request("https://api.osv.dev/v1/querybatch",
            data=json.dumps(query).encode(), headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=45) as response:
            body = response.read(4 * 1024 * 1024 + 1)
        if len(body) > 4 * 1024 * 1024:
            raise ValueError("Advisory response too large")
        rows = json.loads(body).get("results")
        if not isinstance(rows, list) or len(rows) != len(batch):
            raise ValueError("Incomplete advisory response")
        results.extend(rows)
    affected = []
    for dependency, result in zip(dependencies, results):
        if result.get("error"):
            raise ValueError("Advisory provider returned an error")
        if result.get("vulns"):
            affected.append({**dependency, "advisories": sorted({v["id"] for v in result["vulns"]})})
    report = {"source": "https://api.osv.dev/v1/querybatch",
        "queriedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "inventorySha256": hashlib.sha256(raw).hexdigest(), "queried": len(dependencies),
        "dependencies": dependencies, "affected": affected,
        "limitation": "Version matches only; excludes JDK, Maven plugins, actions and unknown/unindexed vulnerabilities."}
    destination.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"queried": len(dependencies), "affectedCoordinates": len(affected),
        "advisoryIds": len({a for d in affected for a in d["advisories"]}), "report": str(destination)}))
    return int(bool(affected))


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print(f"Advisory scan incomplete: {type(error).__name__}: {error}", file=sys.stderr)
        sys.exit(2)
