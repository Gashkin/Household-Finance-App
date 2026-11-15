"""Story ownership and delivery gates. No operation merges a pull request."""
from __future__ import annotations
import argparse
import base64
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid


class Blocked(RuntimeError):
    pass


def require(condition, message):
    if not condition:
        raise Blocked(message)


def now():
    return datetime.now(timezone.utc).isoformat()


def marker(work_id):
    require(re.fullmatch(r"GBD-[1-9][0-9]*", work_id), "Expected canonical Work ID GBD-number")
    return f"<!-- gbd:{work_id} -->"


def story_tags(body):
    return re.findall(r'<!-- gbd:(GBD-[1-9][0-9]*) -->', body or '')


def claim_path(work_id):
    marker(work_id)
    return f".delivery/claims/{work_id}.json"


def definition_path(work_id):
    marker(work_id)
    return f".delivery/stories/{work_id}.json"


def validate_definition(story):
    marker(story['work_id'])
    bootstrap = {'GBD-7': 'story/7-portable-debt-decisions', 'GBD-8': 'story/8-reliable-delivery'}
    canonical = bootstrap.get(story['work_id'], f"story/{story['work_id'].split('-')[1]}-delivery")
    require(story['branch'] == canonical, f'Canonical branch must be {canonical}; titles/slugs cannot create a second claim')
    require(story['branch'].split('/')[1].split('-')[0] == story['work_id'].split('-')[1], "Branch and Work ID differ")
    require(re.fullmatch(r"[a-f0-9-]{32,36}", story['page_id']), "Invalid Notion page ID")
    require(bool(story.get('artifacts')), "Declare verifiable feature/test artifacts before execution")
    for item in story['artifacts']:
        path = item['path']
        needles = item.get('contains')
        require(isinstance(path, str) and path and not path.startswith('/') and '..' not in path.split('/'), 'Unsafe artifact path')
        require(isinstance(needles, list) and bool(needles) and all(isinstance(value, str) and value.strip() for value in needles), 'contains must be a nonempty list of nonblank marker strings')
    exception = story.get('copilot_review_exception')
    if exception is not None:
        require(story['work_id'] == 'GBD-10' and exception == {
            'count': 2, 'authorised_by': 'owner',
            'reason': 'One premature ledger review; final review only after sandbox integration and tests'
        }, 'Only the explicit owner-authorised GBD-10 two-review exception is allowed')
    return story


class Coordinator:
    def __init__(self, git, notion=None):
        self.git, self.notion = git, notion

    def matching_prs(self, story):
        tag = marker(story['work_id'])
        return [p for p in self.git.pulls() if tag in (p.get('body') or '') or p['head']['ref'] == story['branch']]

    def claim(self, story, token=None):
        validate_definition(story)
        require(self.notion is not None, "Notion connection required before claiming")
        page = self.notion.get(story['page_id'])
        require(page['work_id'] == story['work_id'], "Notion Work ID differs from definition")
        require(page['status'] == 'Ready' and not any(page.get(k) for k in ('token', 'branch', 'pr')), "Story is owned or not Ready; inspect it instead of taking over")
        require(not self.matching_prs(story), "An existing PR already represents this story")
        base = self.git.head(story.get('base', 'main'))
        claim = {'work_id': story['work_id'], 'page_id': story['page_id'], 'branch': story['branch'],
                 'token': token or f'claim-token:{uuid.uuid4()}', 'claimed_at': now(), 'base_sha': base,
                 'history': [{'event': 'claim', 'at': now()}]}
        commit = self.git.commit_file(base, claim_path(story['work_id']), claim, f"Claim {story['work_id']}")
        # The create, not the earlier reads, arbitrates simultaneous workers.
        self.git.create_ref(story['branch'], commit)
        try:
            self.sync(story, claim['token'])
        except Exception as error:
            raise Blocked(f"Git claim retained; Notion sync failed. Resume with claim token {claim['token']}; never delete/reclaim the branch. {error}") from error
        return claim

    def owned(self, story, token, ref=None):
        claim = self.git.file(claim_path(story['work_id']), ref or story['branch'])
        require(claim['work_id'] == story['work_id'] and claim['page_id'] == story['page_id']
                and claim['branch'] == story['branch'] and claim['token'] == token, "Claim token/story mismatch; explicit handoff required")
        return claim

    def sync(self, story, token):
        """Repair an interrupted claim/publish sync without acquiring a new claim."""
        require(self.notion is not None, "Notion connection required")
        claim = self.owned(story, token)
        page = self.notion.get(story['page_id'])
        require(page['work_id'] == story['work_id'], "Notion Work ID changed")
        require(page['status'] not in ('Done', 'Superseded'), "Cannot resume completed/retired work")
        permitted = {None, '', token}
        if claim['history'][-1]['event'] == 'handoff':
            permitted.add(claim['history'][-1]['from'])
        require(page.get('token') in permitted, "Notion has a different owner; reconcile explicitly")
        prs = self.matching_prs(story)
        require(len(prs) <= 1, "Duplicate story PRs exist; reconcile before continuing")
        pr = prs[0] if prs else None
        if pr:
            require(pr['state'] == 'open', "PR closed: verify integration, do not open a replacement")
        self.notion.update(story['page_id'], token=token, branch=story['branch'], claimed_at=claim['claimed_at'],
                           status='In Review' if pr else 'In Progress', pr=pr['html_url'] if pr else None)
        saved = self.notion.get(story['page_id'])
        require(saved['token'] == token and saved['branch'] == story['branch'], "Ownership write did not persist; stop execution")
        self.owned(story, token)  # A concurrent handoff invalidates this executor even if Notion briefly lagged.
        return saved

    def handoff(self, story, token, new_token, reason):
        require(new_token and new_token != token and reason.strip(), "Explicit new token and handoff reason required")
        head = self.git.head(story['branch'])
        claim = self.owned(story, token)
        claim['history'].append({'event': 'handoff', 'from': token, 'to': new_token, 'reason': reason, 'at': now()})
        claim['token'] = new_token
        commit = self.git.commit_file(head, claim_path(story['work_id']), claim, f"Handoff {story['work_id']}")
        self.git.cas_ref(story['branch'], head, commit)
        # On failure the Git record remains authoritative and sync is retryable.
        self.sync(story, new_token)
        return claim

    def publish(self, story, token, title, body, base='main', parent_pr=None):
        require(not story_tags(body), 'Description must omit canonical markers; publisher adds exactly one')
        self.sync(story, token)
        if base != 'main':
            require(parent_pr is not None, "Stacked work needs an explicit parent PR")
            parent = self.git.pr(parent_pr)
            require(parent['state'] == 'open' and parent['head']['ref'] == base, "Parent is merged/closed or does not own this base; target main")
        existing = self.matching_prs(story)
        if existing:
            require(existing[0]['state'] == 'open', "Story already has a closed PR; inspect integration")
            return existing[0]
        text = f"{marker(story['work_id'])}\nNotion: https://www.notion.so/{story['page_id'].replace('-', '')}\n{body}"
        try:
            pr = self.git.open_pr(story['branch'], base, title, text)
        except Blocked:
            # A concurrent publisher or lost response may already have created it.
            prs = self.matching_prs(story)
            require(len(prs) == 1 and prs[0]['state'] == 'open', "PR creation failed; no unique existing PR to recover")
            pr = prs[0]
        self.sync(story, token)
        return pr

    def review_gate(self, number, story=None):
        actors = {'copilot', 'copilot-pull-request-reviewer', 'copilot-pull-request-reviewer[bot]'}
        reviews = [r for r in self.git.reviews(number) if r['user']['login'].lower() in actors
                   and r['user'].get('type') == 'Bot' and r['state'] != 'PENDING']
        expected = (story or {}).get('copilot_review_exception', {}).get('count', 1)
        require(len(reviews) == expected, f"Expected exactly {'one' if expected == 1 else 'two'} independent Copilot review(s), found {len(reviews)}. No additional re-reviews")
        require(not any(not t['isResolved'] for t in self.git.threads(number)), "Review conversations remain open; assess, reply with evidence and resolve them")
        return reviews[-1]['id']

    def pr_identity(self, story, number):
        validate_definition(story)
        pr = self.git.pr(number)
        require(story_tags(pr.get('body')) == [story['work_id']], 'PR must contain exactly one canonical story marker')
        require(pr['head']['ref'] == story['branch'], "PR is not on the canonical claim branch")
        claim = self.git.file(claim_path(story['work_id']), pr['head']['sha'])
        require(claim['branch'] == story['branch'] and claim['work_id'] == story['work_id'], 'PR claim does not belong to this story')
        self.owned(story, claim['token'])
        require(len(self.matching_prs(story)) == 1, "Duplicate story PR detected")
        return pr

    def pr_gate(self, story, number):
        pr = self.pr_identity(story, number)
        self.review_gate(number, story)
        return pr

    def pr_readiness(self, story, number):
        pr = self.pr_identity(story, number)
        pending = []
        if pr.get('draft'): pending.append('Draft PR; human merge readiness is not asserted')
        actors = {'copilot', 'copilot-pull-request-reviewer', 'copilot-pull-request-reviewer[bot]'}
        reviews = [r for r in self.git.reviews(number) if r['user']['login'].lower() in actors
                   and r['user'].get('type') == 'Bot' and r['state'] != 'PENDING']
        expected = story.get('copilot_review_exception', {}).get('count', 1)
        require(len(reviews) <= expected, 'Too many Copilot reviews; inspect review sequence')
        if len(reviews) < expected: pending.append('Required Copilot review pending; owner requests it after independent GPT review and corrections')
        if any(not t['isResolved'] for t in self.git.threads(number)): pending.append('Review conversations require disposition and resolution')
        # Missing checks are represented by required_check's pending sentinel; API errors fail closed.
        check = self.git.required_check(pr['head']['sha']) or {}
        if check.get('status') != 'completed': pending.append('Required CI pending on current head')
        else: require(check.get('conclusion') == 'success', 'Required CI failed on current head')
        require(self.git.pr(number)['head']['sha'] == pr['head']['sha'], 'PR changed during readiness verification; refresh')
        return {'pr': number, 'head_sha': pr['head']['sha'], 'review_ready': not pending, 'pending': pending}

    def verify(self, story, number, expected_main=None):
        validate_definition(story)
        pr = self.git.pr(number)
        require(pr.get('merged'), "PR has not been human-merged")
        require(pr.get('merge_commit_sha'), 'Merged PR lacks an integration commit; cannot verify main reachability')
        require(pr['head']['ref'] == story['branch'], 'Merged PR is not the canonical story branch')
        require(pr['base']['ref'] == 'main', 'Merged PR targets a feature branch; integrate to main under human control before Done')
        require(len(self.matching_prs(story)) == 1, 'Duplicate story PRs must be reconciled before Done')
        require(pr.get('merged_by', {}).get('type') == 'User', "Human-controlled merge provenance not confirmed")
        tags = story_tags(pr.get('body'))
        require(tags == [story['work_id']] or (not tags and story.get('legacy_pr') == number), 'PR must contain exactly one canonical story marker')
        main = self.git.head('main')
        require(expected_main is None or expected_main == main, "Main advanced; verify the latest main CI, not a stale event")
        require(self.git.reachable(pr['merge_commit_sha'], main), "Merged away from main (PR #16 failure mode). Retarget/integrate under human control; do not mark Done")
        review = self.review_gate(number, story)
        check = self.git.required_check(main)
        require(check and check['status'] == 'completed' and check['conclusion'] == 'success', "Required build-and-test has not passed on this main SHA")
        hashes = {}
        for artifact in story['artifacts']:
            content = self.git.raw_file(artifact['path'], main)
            require(all(text in content for text in artifact['contains']), f"Expected feature/test artifact absent or changed on main: {artifact['path']}")
            hashes[artifact['path']] = hashlib.sha256(content.encode()).hexdigest()
        require(self.git.head('main') == main, "Main changed during verification; retry against latest main")
        return {'work_id': story['work_id'], 'pr': number, 'main_sha': main, 'merge_sha': pr['merge_commit_sha'],
                'check_url': check['html_url'], 'copilot_review': review, 'artifacts_sha256': hashes,
                'verified_at': now(), 'eligible_for_done': True}

    def finish(self, story, token, number):
        require(self.notion is not None, "Notion connection required to mark Done")
        self.owned(story, token, self.git.pr(number)['head']['sha'])
        receipt = self.verify(story, number)
        page = self.notion.get(story['page_id'])
        require(page['token'] == token and page['pr'] == self.git.pr(number)['html_url'], "Notion ownership/PR differs; do not overwrite")
        require(self.git.head('main') == receipt['main_sha'], 'Main advanced before Done update; repeat verification')
        self.notion.update(story['page_id'], status='Done', verification=json.dumps(receipt, sort_keys=True))
        return receipt


class JsonHTTP:
    def __init__(self, base, token=None, headers=None):
        self.base, self.token, self.headers = base, token, headers or {}

    def call(self, method, path, body=None):
        headers = {'Content-Type': 'application/json', **self.headers}
        if self.token:
            headers['Authorization'] = f'Bearer {self.token}'
        req = urllib.request.Request(self.base + path, data=json.dumps(body).encode() if body is not None else None,
                                     headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                data = response.read()
                return json.loads(data) if data else None
        except urllib.error.HTTPError as error:
            # Never print response bodies or credentials from upstream services.
            code = error.code
            error.close()
            raise Blocked(f"{method} {path.split('?')[0]} returned HTTP {code}; inspect ownership/state before retrying writes") from error
        except urllib.error.URLError as error:
            raise Blocked("Remote service unavailable; writes may have succeeded. Use sync/recovery rather than re-claiming") from error


class GitHub:
    def __init__(self, repo, token=None, http=None):
        require(re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repo), 'Expected owner/repository')
        self.repo, self.http = repo, http or JsonHTTP('https://api.github.com', token, {'X-GitHub-Api-Version': '2026-03-10', 'Accept': 'application/vnd.github+json'})
        self.prefix = f'/repos/{repo}'

    def api(self, method, path, body=None):
        return self.http.call(method, self.prefix + path, body)

    def pages(self, path, key=None):
        result, page = [], 1
        while True:
            data = self.api('GET', path + ('&' if '?' in path else '?') + f'per_page=100&page={page}')
            rows = data[key] if key else data
            result.extend(rows)
            if len(rows) < 100:
                return result
            page += 1

    def head(self, branch):
        return self.api('GET', '/git/ref/heads/' + urllib.parse.quote(branch, safe=''))['object']['sha']

    def create_ref(self, branch, sha):
        return self.api('POST', '/git/refs', {'ref': 'refs/heads/' + branch, 'sha': sha})

    def commit_file(self, base, path, value, message):
        tree = self.api('GET', f'/git/commits/{base}')['tree']['sha']
        tree = self.api('POST', '/git/trees', {'base_tree': tree, 'tree': [{'path': path, 'mode': '100644', 'type': 'blob', 'content': json.dumps(value, indent=2) + '\n'}]})['sha']
        return self.api('POST', '/git/commits', {'message': message, 'tree': tree, 'parents': [base]})['sha']

    def cas_ref(self, branch, before, after):
        repository_id = self.api('GET', '')['node_id']
        response = self.http.call('POST', '/graphql', {'query': 'mutation($input:UpdateRefsInput!){updateRefs(input:$input){clientMutationId}}',
            'variables': {'input': {'repositoryId': repository_id, 'refUpdates': [{'name': 'refs/heads/' + branch, 'beforeOid': before, 'afterOid': after, 'force': False}]}}})
        require(not response.get('errors'), 'Branch changed during handoff; no takeover performed. Re-read claim and retry deliberately')

    def raw_file(self, path, ref):
        result = self.api('GET', '/contents/' + urllib.parse.quote(path, safe='/') + '?ref=' + urllib.parse.quote(ref, safe=''))
        require(result.get('encoding') == 'base64', 'Artifact is not a supported text file')
        return base64.b64decode(result['content']).decode()

    def file(self, path, ref):
        return json.loads(self.raw_file(path, ref))

    def pulls(self):
        return self.pages('/pulls?state=all')

    def pr(self, number):
        pr = self.api('GET', f'/pulls/{int(number)}')
        # REST 2026 no longer returns the integration SHA; normalize from GraphQL.
        if pr.get('merged') and not pr.get('merge_commit_sha'):
            owner, name = self.repo.split('/')
            data = self.http.call('POST', '/graphql', {
                'query': 'query($owner:String!,$name:String!,$number:Int!){repository(owner:$owner,name:$name){pullRequest(number:$number){mergeCommit{oid}}}}',
                'variables': {'owner': owner, 'name': name, 'number': int(number)}})
            require(not data.get('errors'), 'Cannot retrieve the merged integration commit')
            commit = data['data']['repository']['pullRequest']['mergeCommit']
            pr['merge_commit_sha'] = commit['oid'] if commit else None
        return pr

    def open_pr(self, head, base, title, body):
        return self.api('POST', '/pulls', {'head': head, 'base': base, 'title': title, 'body': body})

    def reviews(self, number):
        return self.pages(f'/pulls/{int(number)}/reviews')

    def threads(self, number):
        owner, name = self.repo.split('/')
        result, cursor = [], None
        while True:
            query = 'query($owner:String!,$name:String!,$number:Int!,$cursor:String){repository(owner:$owner,name:$name){pullRequest(number:$number){reviewThreads(first:100,after:$cursor){nodes{isResolved} pageInfo{hasNextPage endCursor}}}}}'
            data = self.http.call('POST', '/graphql', {'query': query, 'variables': {'owner': owner, 'name': name, 'number': int(number), 'cursor': cursor}})
            require(not data.get('errors'), 'Cannot verify review thread state')
            connection = data['data']['repository']['pullRequest']['reviewThreads']
            result.extend(connection['nodes'])
            if not connection['pageInfo']['hasNextPage']:
                return result
            cursor = connection['pageInfo']['endCursor']

    def reachable(self, ancestor, descendant):
        return self.api('GET', f'/compare/{ancestor}...{descendant}')['status'] in ('ahead', 'identical')

    def required_check(self, sha):
        checks = [c for c in self.pages(f'/commits/{sha}/check-runs', 'check_runs') if c['name'] == 'build-and-test' and c.get('app', {}).get('slug') == 'github-actions']
        return max(checks, key=lambda c: c['id']) if checks else None


class Notion:
    fields = {'token': 'Claim Session ID', 'branch': 'Claim Branch', 'claimed_at': 'Claimed At', 'status': 'Status', 'pr': 'GitHub PR', 'verification': 'Verification'}

    def __init__(self, token, http=None):
        self.http = http or JsonHTTP('https://api.notion.com/v1', token, {'Notion-Version': '2025-09-03'})

    def get(self, page_id):
        props = self.http.call('GET', '/pages/' + page_id)['properties']
        unique = props['Work ID']['unique_id']
        result = {'work_id': f"{unique['prefix']}-{unique['number']}"}
        for key, name in self.fields.items():
            prop = props[name]
            if key == 'status':
                result[key] = (prop['status'] or {}).get('name')
            elif key == 'pr':
                result[key] = prop['url']
            elif key == 'claimed_at':
                result[key] = (prop['date'] or {}).get('start')
            else:
                result[key] = ''.join(t.get('plain_text', t.get('text', {}).get('content', '')) for t in prop['rich_text'])
        return result

    def update(self, page_id, **values):
        props = {}
        for key, value in values.items():
            name = self.fields[key]
            if key == 'status': props[name] = {'status': {'name': value}}
            elif key == 'pr': props[name] = {'url': value}
            elif key == 'claimed_at': props[name] = {'date': {'start': value}}
            else: props[name] = {'rich_text': [{'text': {'content': value[i:i+1900]}} for i in range(0, len(value or ''), 1900)]}
        return self.http.call('PATCH', '/pages/' + page_id, {'properties': props})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['claim', 'sync', 'handoff', 'publish', 'pr-check', 'verify', 'finish', 'audit-main'])
    parser.add_argument('--repo', default='Gashkin/Household-Finance-App')
    parser.add_argument('--definition')
    parser.add_argument('--token', default=os.environ.get('GBD_CLAIM_TOKEN'))
    parser.add_argument('--new-token')
    parser.add_argument('--reason', default='')
    parser.add_argument('--pr', type=int)
    parser.add_argument('--title')
    parser.add_argument('--body-file')
    parser.add_argument('--base', default='main')
    parser.add_argument('--parent-pr', type=int)
    parser.add_argument('--main-sha')
    parser.add_argument('--receipt')
    args = parser.parse_args()
    try:
        git = GitHub(args.repo, os.environ.get('GITHUB_TOKEN') or os.environ.get('GH_TOKEN'))
        notion = Notion(os.environ['NOTION_TOKEN']) if os.environ.get('NOTION_TOKEN') else None
        service = Coordinator(git, notion)
        if args.command == 'audit-main':
            receipts = []
            for path in sorted(Path('.delivery/stories').glob('GBD-*.json')):
                story = validate_definition(json.loads(path.read_text()))
                prs = service.matching_prs(story)
                if story.get('legacy_pr'):
                    prs = [git.pr(story['legacy_pr'])]
                for pr in prs:
                    if pr.get('merged_at') or pr.get('merged'):
                        receipts.append(service.verify(story, pr['number'], args.main_sha))
            result = {'verified_stories': receipts, 'note': 'Read-only audit. Done requires finish or connector update after inspecting receipts.'}
        else:
            require(args.definition, '--definition required')
            story = validate_definition(json.loads(Path(args.definition).read_text()))
            if args.command == 'claim': result = service.claim(story, args.token)
            elif args.command == 'sync': result = service.sync(story, args.token)
            elif args.command == 'handoff': result = service.handoff(story, args.token, args.new_token, args.reason)
            elif args.command == 'publish':
                require(args.title and args.body_file, '--title and --body-file required')
                result = service.publish(story, args.token, args.title, Path(args.body_file).read_text(), args.base, args.parent_pr)
            else:
                require(args.pr, '--pr required')
                if args.command == 'pr-check': result = service.pr_gate(story, args.pr)
                elif args.command == 'verify': result = service.verify(story, args.pr, args.main_sha)
                else: result = service.finish(story, args.token, args.pr)
        output = json.dumps({'ok': True, 'result': result}, indent=2)
        if args.receipt: Path(args.receipt).write_text(output + '\n')
        print(output)
    except (Blocked, KeyError, ValueError, OSError) as error:
        print(json.dumps({'ok': False, 'blocked': str(error)}), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
