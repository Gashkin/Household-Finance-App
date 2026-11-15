"""Read-only, aggregate historical exposure triage. Never print paths or contents.

This is NOT a clean-bill-of-health or a complete secret detector. Binary financial
data needs owner-local investigation. No rewriting, uploading or credential use.
"""
import collections
import json
import re
import subprocess


def git(*args):
    return subprocess.check_output(['git', *args], stderr=subprocess.DEVNULL)


def category(path):
    if re.search(r'\.(?:db|trace\.db)$', path, re.I):
        return 'database'
    if re.search(r'\.(?:xlsx|xls)$', path, re.I):
        return 'workbook'
    if path.endswith('.sql'):
        return 'SQL fixtures'
    if re.search(r'(analysis.*\.(?:json|csv|txt)|extracted.*\.json)', path, re.I):
        return 'generated financial analysis'
    return None


def main():
    paths = collections.defaultdict(set)
    candidates = collections.Counter()
    seen = set()
    scanned = skipped = 0
    patterns = {
        'complete_private_key_block': rb'-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----[\r\n]+[A-Za-z0-9+/=\r\n]+-----END (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----',
        'github_token': rb'(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{50,})',
        'aws_access_key': rb'(?<![A-Z0-9])(?:AKIA|ASIA)[A-Z0-9]{16}(?![A-Z0-9])',
        'jwt': rb'eyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{20,}',
        'assigned_secret': rb'(?im)^\s*(?:GITHUB_TOKEN|GH_TOKEN|NOTION_TOKEN|[A-Z_]*(?:API_KEY|SECRET_KEY|ACCESS_TOKEN|PASSWORD))\s*=\s*["\']?[^\s"\'$#{}]{12,}',
    }
    for row in git('rev-list', '--objects', '--all').decode('utf-8', errors='replace').splitlines():
        oid, _, path = row.partition(' ')
        if not path or any(part in path for part in ('node_modules/', 'package-lock.json', 'target/', '.map')):
            continue
        kind = category(path)
        if kind:
            paths[kind].add(path)
        if oid in seen:
            continue
        seen.add(oid)
        if git('cat-file', '-t', oid).strip() != b'blob':
            continue
        if int(git('cat-file', '-s', oid)) > 20 * 1024 * 1024:
            skipped += 1
            continue
        data = git('cat-file', '-p', oid)
        scanned += 1
        for name, pattern in patterns.items():
            if re.search(pattern, data):
                candidates[name] += 1
    print(json.dumps({
        'reachable_commits': len(git('rev-list', '--all').splitlines()),
        'shallow': git('rev-parse', '--is-shallow-repository').strip() == b'true',
        'history_categories_unique_paths': {kind: len(values) for kind, values in paths.items()},
        'unique_non_dependency_blobs_scanned': scanned,
        'blobs_over_20MiB_not_content_scanned': skipped,
        'pattern_candidate_blob_counts': dict(candidates),
        'limitations': 'Reachable local refs only; dependencies excluded; no proof of absence. Does not inspect forks, caches, artifacts, hidden refs, external copies or decode private workbook/database contents. Rotate any actually exposed credential after owner-local assessment; do not publish findings containing secrets.',
    }, sort_keys=True))


if __name__ == '__main__':
    main()
