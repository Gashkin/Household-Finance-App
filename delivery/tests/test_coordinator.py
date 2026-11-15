"""Stateful integration simulations. GitHub/Notion network credentials are not needed."""
from concurrent.futures import ThreadPoolExecutor
from copy import deepcopy
import json
import threading
import tempfile
from pathlib import Path
from delivery.ci import event_check, publish_readiness
import unittest
from unittest.mock import patch
import urllib.error
from delivery.coordinator import Blocked, Coordinator, GitHub, JsonHTTP, Notion, claim_path, marker, validate_definition

STORY = {'work_id': 'GBD-10', 'page_id': '11111111-1111-1111-1111-111111111111', 'branch': 'story/10-delivery',
         'artifacts': [{'path': 'feature.py', 'contains': ['working capability']}, {'path': 'tests.py', 'contains': ['regression']}]}


class MemoryGit:
    """Transactional fake remote; changes persist across independent worker objects."""
    def __init__(self):
        self.lock = threading.RLock()
        self.refs = {'main': 'main-1'}
        self.trees = {'main-1': {'feature.py': 'working capability', 'tests.py': 'regression'}}
        self.parents = {'main-1': None}
        self.prs = []
        self.review_items = [{'id': 123, 'state': 'COMMENTED', 'user': {'login': 'copilot-pull-request-reviewer[bot]', 'type': 'Bot'}}]
        self.thread_items = []
        self.check = {'id': 1, 'status': 'completed', 'conclusion': 'success', 'html_url': 'https://example.test/check'}
        self.open_barrier = None

    def head(self, branch):
        with self.lock: return self.refs[branch]

    def create_ref(self, branch, sha):
        with self.lock:
            if branch in self.refs: raise Blocked('Reference already exists')
            self.refs[branch] = sha

    def commit_file(self, base, path, value, message):
        with self.lock:
            sha = f'commit-{len(self.trees)}'
            self.trees[sha] = {**deepcopy(self.trees[base]), path: json.dumps(value)}
            self.parents[sha] = base
            return sha

    def cas_ref(self, branch, before, after):
        with self.lock:
            if self.refs[branch] != before: raise Blocked('Concurrent update')
            self.refs[branch] = after

    def raw_file(self, path, ref):
        with self.lock: return self.trees[self.refs.get(ref, ref)][path]

    def file(self, path, ref): return json.loads(self.raw_file(path, ref))
    def pulls(self):
        with self.lock: return deepcopy(self.prs)
    def pr(self, number):
        with self.lock: return deepcopy(next(p for p in self.prs if p['number'] == number))

    def open_pr(self, head, base, title, body):
        if self.open_barrier: self.open_barrier.wait(timeout=5)
        with self.lock:
            if any(p['head']['ref'] == head for p in self.prs): raise Blocked('PR already exists')
            pr = {'number': len(self.prs)+1, 'head': {'ref': head, 'sha': self.refs[head]}, 'base': {'ref': base, 'sha': self.refs[base]},
                  'title': title, 'body': body, 'state': 'open', 'merged': False, 'merged_at': None,
                  'html_url': f'https://example.test/pull/{len(self.prs)+1}'}
            self.prs.append(pr)
            return deepcopy(pr)

    def human_merge(self, number, to_main=True):
        with self.lock:
            pr = self.prs[number-1]
            sha = 'merge-' + str(number)
            self.trees[sha] = deepcopy(self.trees[pr['head']['sha']])
            self.parents[sha] = self.refs['main'] if to_main else pr['head']['sha']
            pr.update(merged=True, merged_at='now', state='closed', merge_commit_sha=sha, merged_by={'type': 'User'})
            if to_main: self.refs['main'] = sha

    def reachable(self, ancestor, descendant):
        while descendant:
            if descendant == ancestor: return True
            descendant = self.parents.get(descendant)
        return False
    def reviews(self, number): return [{**deepcopy(r), 'commit_id': r.get('commit_id', self.pr(number)['head']['sha'])} for r in self.review_items]
    def threads(self, number): return deepcopy(self.thread_items)
    def required_check(self, sha): return deepcopy(self.check)


class MemoryNotion:
    def __init__(self):
        self.lock = threading.RLock()
        self.page = {'work_id': STORY['work_id'], 'status': 'Ready', 'token': '', 'branch': '', 'pr': None}
        self.fail_update = False
        self.ready_barrier = None
        self.seen = set()

    def get(self, page_id):
        with self.lock:
            result = deepcopy(self.page)
            first = threading.get_ident() not in self.seen
            self.seen.add(threading.get_ident())
        if self.ready_barrier and first: self.ready_barrier.wait(timeout=5)
        return result

    def update(self, page_id, **values):
        with self.lock:
            if self.fail_update:
                self.fail_update = False
                raise Blocked('Simulated Notion outage')
            self.page.update(values)


class CoordinationIntegrationTests(unittest.TestCase):
    def setUp(self):
        self.git, self.notion = MemoryGit(), MemoryNotion()
        self.c = Coordinator(self.git, self.notion)

    def ready_pr(self):
        self.c.claim(STORY, 'owner-A')
        return self.c.publish(STORY, 'owner-A', 'Feature', 'One complete capability')

    def test_two_simultaneous_workers_only_one_claims(self):
        self.notion.ready_barrier = threading.Barrier(2)
        def worker(token):
            try: return Coordinator(self.git, self.notion).claim(STORY, token)['token']
            except Blocked: return 'rejected'
        with ThreadPoolExecutor(2) as pool:
            results = list(pool.map(worker, ['worker-A', 'worker-B']))
        self.assertEqual(results.count('rejected'), 1)
        owner = next(value for value in results if value != 'rejected')
        self.assertEqual(self.notion.page['token'], owner)
        self.assertEqual(self.git.file(claim_path(STORY['work_id']), STORY['branch'])['token'], owner)

    def test_failed_notion_write_preserves_claim_and_recovers_same_owner(self):
        self.notion.fail_update = True
        with self.assertRaisesRegex(Blocked, 'Git claim retained'):
            self.c.claim(STORY, 'owner-A')
        with self.assertRaises(Blocked): self.c.claim(STORY, 'thief')
        self.c.sync(STORY, 'owner-A')
        self.assertEqual(self.notion.page['status'], 'In Progress')
        with self.assertRaisesRegex(Blocked, 'handoff'): self.c.sync(STORY, 'thief')

    def test_concurrent_publish_yields_one_pr_and_recovers_lost_create(self):
        self.c.claim(STORY, 'owner-A')
        self.git.open_barrier = threading.Barrier(2)
        with ThreadPoolExecutor(2) as pool:
            results = list(pool.map(lambda _: self.c.publish(STORY, 'owner-A', 'Feature', 'Value'), range(2)))
        self.assertEqual([p['number'] for p in results], [1, 1])
        self.assertEqual(len(self.git.prs), 1)
        self.assertEqual(self.notion.page['status'], 'In Review')

    def test_explicit_handoff_retains_history_and_rejects_old_owner(self):
        self.c.claim(STORY, 'owner-A')
        self.c.handoff(STORY, 'owner-A', 'owner-B', 'Verified execution handoff')
        self.assertEqual(self.notion.page['token'], 'owner-B')
        with self.assertRaises(Blocked): self.c.publish(STORY, 'owner-A', 'No', 'No')
        self.assertEqual(self.c.owned(STORY, 'owner-B')['history'][-1]['from'], 'owner-A')

    def test_handoff_cas_cannot_overwrite_another_commit(self):
        self.c.claim(STORY, 'owner-A')
        before = self.git.head(STORY['branch'])
        new = self.git.commit_file(before, 'file', {}, 'Independent commit')
        self.git.cas_ref(STORY['branch'], before, new)
        with self.assertRaises(Blocked): self.git.cas_ref(STORY['branch'], before, 'stale')
        self.assertEqual(self.git.head(STORY['branch']), new)

    def test_verified_human_merge_is_only_route_to_done(self):
        pr = self.ready_pr()
        with self.assertRaises(Blocked): self.c.finish(STORY, 'owner-A', pr['number'])
        self.git.human_merge(pr['number'])
        receipt = self.c.finish(STORY, 'owner-A', pr['number'])
        self.assertTrue(receipt['eligible_for_done'])
        self.assertEqual(self.notion.page['status'], 'Done')
        self.assertEqual(set(receipt['artifacts_sha256']), {'feature.py', 'tests.py'})

    def test_pr16_wrong_base_regression_does_not_mark_done(self):
        pr = self.ready_pr()
        self.git.human_merge(pr['number'], to_main=False)
        with self.assertRaisesRegex(Blocked, 'Merged away from main'):
            self.c.finish(STORY, 'owner-A', pr['number'])
        self.assertEqual(self.notion.page['status'], 'In Review')

    def test_missing_main_artifact_failed_ci_and_stale_main_are_blocked(self):
        pr = self.ready_pr(); self.git.human_merge(pr['number'])
        self.git.check['conclusion'] = 'failure'
        with self.assertRaisesRegex(Blocked, 'build-and-test'): self.c.verify(STORY, 1)
        self.git.check['conclusion'] = 'success'
        self.git.trees[self.git.head('main')]['feature.py'] = 'missing feature'
        with self.assertRaisesRegex(Blocked, 'artifact'): self.c.verify(STORY, 1)
        with self.assertRaisesRegex(Blocked, 'advanced'): self.c.verify(STORY, 1, expected_main='old-main')

    def test_one_review_and_resolved_threads_are_required_without_requesting_reviews(self):
        self.ready_pr()
        self.git.review_items = []
        with self.assertRaisesRegex(Blocked, 'exactly one'): self.c.pr_gate(STORY, 1)
        self.git.review_items = [{'id': i, 'state': 'COMMENTED', 'user': {'login': 'Copilot', 'type': 'Bot'}} for i in [1, 2]]
        with self.assertRaisesRegex(Blocked, 'found 2'): self.c.pr_gate(STORY, 1)
        self.git.review_items = self.git.review_items[:1]
        self.git.thread_items = [{'isResolved': False}]
        with self.assertRaisesRegex(Blocked, 'conversations'): self.c.pr_gate(STORY, 1)
        self.git.thread_items[0]['isResolved'] = True
        self.assertEqual(self.c.pr_gate(STORY, 1)['number'], 1)

    def test_owner_exception_is_limited_to_two_reviews_for_gbd10(self):
        self.ready_pr()
        story = {**STORY, 'copilot_review_exception': {'count': 2, 'authorised_by': 'owner',
            'reason': 'One premature ledger review; final review only after sandbox integration and tests'}}
        with self.assertRaisesRegex(Blocked, 'exactly two'): self.c.pr_gate(story, 1)
        self.git.review_items.append({**self.git.review_items[0], 'id': 124})
        self.assertEqual(self.c.pr_gate(story, 1)['number'], 1)
        with self.assertRaisesRegex(Blocked, 'found 2'): self.c.pr_gate(STORY, 1)
        self.git.review_items.append({**self.git.review_items[0], 'id': 125})
        with self.assertRaisesRegex(Blocked, 'found 3'): self.c.pr_gate(story, 1)
        with self.assertRaises(Blocked): validate_definition({**story, 'work_id': 'GBD-14', 'branch': 'story/14-delivery'})
        with self.assertRaises(Blocked): validate_definition({**story, 'copilot_review_exception': {'count': 3}})

    def test_closed_pr_cannot_be_bypassed_and_slug_cannot_change_claim_identity(self):
        self.ready_pr(); self.git.prs[0]['state'] = 'closed'
        with self.assertRaises(Blocked): self.c.publish(STORY, 'owner-A', 'Replacement', 'No')
        with self.assertRaisesRegex(Blocked, 'Canonical branch'):
            validate_definition({**STORY, 'branch': 'story/10-another-slug'})

    def test_unknown_owner_mismatched_story_or_stale_parent_are_blocked(self):
        self.c.claim(STORY, 'owner-A')
        with self.assertRaises(Blocked): self.c.publish(STORY, 'owner-B', 'No', 'No')
        with self.assertRaisesRegex(Blocked, 'parent PR'):
            self.c.publish(STORY, 'owner-A', 'No', 'No', base='feature/other')
        self.notion.page['token'] = 'owner-B'
        with self.assertRaisesRegex(Blocked, 'different owner'): self.c.sync(STORY, 'owner-A')


    def test_finish_uses_merge_head_claim_after_branch_deletion(self):
        pr = self.ready_pr(); self.git.human_merge(pr['number'])
        del self.git.refs[STORY['branch']]
        self.assertTrue(self.c.finish(STORY, 'owner-A', 1)['eligible_for_done'])

    def test_ci_event_checks_wrong_base_and_rejects_missing_story_marker(self):
        pr = self.ready_pr()
        path = '.delivery/stories/GBD-10.json'
        self.git.trees[pr['head']['sha']][path] = json.dumps(STORY)
        self.assertTrue(event_check(self.git, 'pull_request_review', {'pull_request': pr})['review_ready'])
        without_marker = {**pr, 'body': 'No identity'}
        self.git.prs[0]['body'] = 'No identity'
        with self.assertRaisesRegex(Blocked, 'marker'):
            event_check(self.git, 'pull_request', {'pull_request': without_marker})
        self.git.prs[0]['body'] = pr['body']
        self.git.human_merge(1, to_main=False)
        with self.assertRaisesRegex(Blocked, 'Merged away from main'):
            event_check(self.git, 'pull_request', {'action': 'closed', 'pull_request': self.git.pr(1)})

    def test_ci_only_audits_successful_main_push_ci(self):
        result = event_check(self.git, 'workflow_run', {'workflow_run': {'head_branch': 'fork', 'event': 'pull_request'}})
        self.assertIn('skipped', result)
        with self.assertRaisesRegex(Blocked, 'Main CI failed'):
            event_check(self.git, 'workflow_run', {'workflow_run': {'head_branch': 'main', 'event': 'push', 'conclusion': 'failure'}})
        pr = self.ready_pr(); self.git.human_merge(1)
        with tempfile.TemporaryDirectory() as folder:
            definition = Path(folder) / 'GBD-10.json'; definition.write_text(json.dumps(STORY))
            with patch('delivery.ci.Path.glob', return_value=[definition]):
                result = event_check(self.git, 'workflow_run', {'workflow_run': {'head_branch': 'main', 'event': 'push', 'conclusion': 'success', 'head_sha': self.git.head('main')}})
        self.assertEqual(len(result['verified_stories']), 1)
        self.assertFalse(result['notion_updated'])
        self.assertEqual(self.notion.page['status'], 'In Review')

    def test_different_pr_branch_cannot_claim_completion_for_this_story(self):
        self.ready_pr(); self.git.prs[0]['head']['ref'] = 'unrelated'; self.git.human_merge(1)
        with self.assertRaisesRegex(Blocked, 'canonical story branch'):
            self.c.verify(STORY, 1)


    def test_feature_target_is_blocked_even_when_its_commit_later_reaches_main(self):
        self.ready_pr(); self.git.prs[0]['base']['ref'] = 'feature/parent'; self.git.human_merge(1)
        with self.assertRaisesRegex(Blocked, 'targets a feature branch'): self.c.verify(STORY, 1)

    def test_artifact_markers_cannot_be_strings_empty_blank_or_nonstrings(self):
        for markers in ['a', [], [''], ['  '], [1], None]:
            with self.subTest(markers=markers), self.assertRaisesRegex(Blocked, 'contains'):
                validate_definition({**STORY, 'artifacts': [{'path': 'file', 'contains': markers}]})

    def test_duplicate_or_mixed_story_markers_are_rejected(self):
        self.ready_pr()
        for extra in [marker(STORY['work_id']), marker('GBD-11')]:
            self.git.prs[0]['body'] = marker(STORY['work_id']) + extra
            with self.assertRaisesRegex(Blocked, 'exactly one'): self.c.pr_gate(STORY, 1)
        with self.assertRaisesRegex(Blocked, 'omit canonical markers'):
            self.c.publish(STORY, 'owner-A', 'No', marker(STORY['work_id']))

    def test_supported_refresh_events_use_pr_data_not_commands(self):
        pr = self.ready_pr()
        self.git.trees[pr['head']['sha']]['.delivery/stories/GBD-10.json'] = json.dumps(STORY)
        self.assertTrue(event_check(self.git, 'issue_comment', {'issue': {'number': 1, 'pull_request': {'url': 'https://example.test/pull/1'}}, 'comment': {'body': 'untrusted text'}})['review_ready'])
        self.assertTrue(event_check(self.git, 'workflow_dispatch', {'inputs': {'pr_number': '1'}})['review_ready'])
        self.assertIn('skipped', event_check(self.git, 'issue_comment', {'issue': {'number': 5}}))


    def test_draft_pending_review_is_not_failure_or_merge_ready(self):
        pr = self.ready_pr()
        self.git.prs[0]['draft'] = True
        self.git.review_items = []
        result = self.c.pr_readiness(STORY, 1)
        self.assertFalse(result['review_ready'])
        self.assertIn('Draft PR', result['pending'][0])
        with self.assertRaises(Blocked): self.c.pr_gate(STORY, 1)
        self.git.prs[0]['body'] += marker('GBD-99')
        with self.assertRaisesRegex(Blocked, 'exactly one'): self.c.pr_readiness(STORY, 1)

    def test_ready_event_and_ci_completion_refresh_exact_head(self):
        pr = self.ready_pr()
        self.git.trees[pr['head']['sha']]['.delivery/stories/GBD-10.json'] = json.dumps(STORY)
        self.git.review_items = []
        result = event_check(self.git, 'pull_request_target', {'action': 'ready_for_review', 'pull_request': pr})
        self.assertFalse(result['review_ready'])
        self.git.review_items = [{'id': 1, 'state': 'COMMENTED', 'user': {'login': 'Copilot', 'type': 'Bot'}}]
        self.git.check = None
        self.assertFalse(self.c.pr_readiness(STORY, 1)['review_ready'])
        self.git.check = {'status': 'completed', 'conclusion': 'success'}
        event = {'workflow_run': {'event': 'pull_request', 'head_sha': pr['head']['sha']}}
        self.assertTrue(event_check(self.git, 'workflow_run', event)['review_ready'])
        event['workflow_run']['head_sha'] = 'old-head'
        self.assertIn('skipped', event_check(self.git, 'workflow_run', event))
        self.git.check['conclusion'] = 'failure'
        with self.assertRaisesRegex(Blocked, 'CI failed'): self.c.pr_readiness(STORY, 1)
        self.git.check['conclusion'] = 'success'
        self.git.thread_items = [{'isResolved': False}]
        self.assertFalse(self.c.pr_readiness(STORY, 1)['review_ready'])
        self.git.review_items *= 2
        with self.assertRaisesRegex(Blocked, 'Too many'): self.c.pr_readiness(STORY, 1)

    def test_stale_review_requires_bound_owner_delta_receipt_without_another_review(self):
        pr = self.ready_pr()
        self.git.review_items[0]['commit_id'] = 'reviewed-old-head'
        self.assertFalse(self.c.pr_readiness(STORY, 1)['review_ready'])
        receipt = {'head_sha': pr['head']['sha'], 'base_sha': pr['base']['sha'],
                   'review_id': self.git.review_items[0]['id'], 'reviewed_sha': 'reviewed-old-head'}
        prefix = pr['body']
        def attest(value): self.git.prs[0]['body'] = prefix + '\n<!-- gbd-owned-verification ' + json.dumps(value) + ' -->'
        attest(receipt)
        result = self.c.pr_readiness(STORY, 1)
        self.assertTrue(result['review_ready'])
        self.assertTrue(result['owned_delta_verified'])
        self.assertEqual(result['copilot_reviewed_sha'], 'reviewed-old-head')
        for field in ['head_sha', 'base_sha', 'review_id', 'reviewed_sha']:
            bad = {**receipt, field: 99 if field == 'review_id' else 'wrong'}
            attest(bad)
            self.assertFalse(self.c.pr_readiness(STORY, 1)['review_ready'], field)
        attest({**receipt, 'review_id': True})
        self.assertFalse(self.c.pr_readiness(STORY, 1)['review_ready'])
        attest(receipt)
        self.git.prs[0]['body'] += '\n<!-- gbd-owned-verification ' + json.dumps(receipt) + ' -->'
        self.assertFalse(self.c.pr_readiness(STORY, 1)['review_ready'])
        self.git.prs[0]['body'] = prefix + '\n<!-- gbd-owned-verification invalid-json -->'
        self.assertFalse(self.c.pr_readiness(STORY, 1)['review_ready'])
        attest(receipt)
        self.git.check = {'status': 'completed', 'conclusion': 'failure'}
        with self.assertRaisesRegex(Blocked, 'CI failed'): self.c.pr_readiness(STORY, 1)
        self.git.check = {'status': 'completed', 'conclusion': 'success'}
        self.git.review_items[0]['commit_id'] = None
        self.assertFalse(self.c.pr_readiness(STORY, 1)['review_ready'])
        self.assertEqual(len(self.git.review_items), 1)



class WireContractTests(unittest.TestCase):
    def test_readiness_check_remains_pending_then_completes_and_fails_closed(self):
        class Remote:
            calls = []
            def pr(self, number): return {'head': {'sha': 'current'}}
            def pages(self, path, key): return [{'id': 8, 'name': 'Story merge readiness', 'app': {'slug': 'github-actions'}}]
            def api(self, method, path, body): self.calls.append((method, path, body))
        git = Remote()
        pending = {'pr': 1, 'head_sha': 'current', 'review_ready': False, 'pending': ['Copilot pending']}
        publish_readiness(git, pending)
        self.assertEqual(git.calls[-1][2]['status'], 'in_progress')
        self.assertNotIn('conclusion', git.calls[-1][2])
        publish_readiness(git, {**pending, 'review_ready': True, 'pending': []})
        self.assertEqual(git.calls[-1][2]['conclusion'], 'success')
        publish_readiness(git, {**pending, 'blocked': 'Invalid identity'})
        self.assertEqual(git.calls[-1][2]['conclusion'], 'failure')
        before = len(git.calls)
        with self.assertRaisesRegex(Blocked, 'Head changed'):
            publish_readiness(git, {**pending, 'head_sha': 'old'})
        self.assertEqual(before, len(git.calls))

    def test_create_ref_is_post_only_and_handoff_has_atomic_before_oid(self):
        class HTTP:
            calls = []
            def call(self, method, path, body=None):
                self.calls.append((method, path, body))
                return {'node_id': 'repo-id'} if method == 'GET' else {'data': {}}
        http = HTTP(); git = GitHub('owner/repo', http=http)
        git.create_ref('story/10-delivery', 'sha')
        self.assertEqual(http.calls[-1], ('POST', '/repos/owner/repo/git/refs', {'ref': 'refs/heads/story/10-delivery', 'sha': 'sha'}))
        git.cas_ref('story/10-delivery', 'before', 'after')
        update = http.calls[-1][2]['variables']['input']['refUpdates'][0]
        self.assertEqual(update['beforeOid'], 'before'); self.assertFalse(update['force'])

    def test_notion_updates_are_partial_and_preserve_status_and_full_timestamp_types(self):
        class HTTP:
            def call(self, method, path, body=None): self.request = (method, path, body); return {}
        http = HTTP(); client = Notion('unused', http=http)
        client.update('page', token='claim', claimed_at='2026-11-14T11:45:00Z', status='In Progress')
        props = http.request[2]['properties']
        self.assertEqual(props['Claimed At']['date']['start'], '2026-11-14T11:45:00Z')
        self.assertNotIn('Acceptance Criteria', props)
        self.assertEqual(props['Status']['status']['name'], 'In Progress')

    def test_github_reads_all_pages_and_does_not_ignore_graphql_conflicts(self):
        class HTTP:
            def call(self, method, path, body=None):
                if '/reviews?' in path:
                    return [{'id': i} for i in range(100)] if path.endswith('&page=1') else [{'id': 100}]
                if method == 'GET': return {'node_id': 'repository'}
                return {'errors': [{'message': 'Concurrent branch update'}]}
        git = GitHub('owner/repo', http=HTTP())
        self.assertEqual(len(git.reviews(1)), 101)
        with self.assertRaisesRegex(Blocked, 'Branch changed'):
            git.cas_ref('story/10-delivery', 'old', 'new')

    def test_merged_rest_response_without_sha_uses_graphql_integration_commit(self):
        class HTTP:
            def call(self, method, path, body=None):
                if method == 'GET': return {'number': 18, 'merged': True}
                return {'data': {'repository': {'pullRequest': {'mergeCommit': {'oid': 'integrated-sha'}}}}}
        self.assertEqual(GitHub('owner/repo', http=HTTP()).pr(18)['merge_commit_sha'], 'integrated-sha')

    def test_token_bearing_workflow_never_checks_out_pr_code(self):
        workflow = Path('.github/workflows/delivery-integrity.yml').read_text()
        required = Path('.github/workflows/frontend-ci.yml').read_text()
        self.assertIn('pull_request_target:', workflow)
        self.assertIn('ref: main', workflow)
        self.assertNotIn('ref: ${{', workflow)
        self.assertNotIn('pull_request_review_thread:', workflow)
        self.assertNotIn('GITHUB_TOKEN:', required)
        self.assertNotIn('self-hosted', workflow)
        for permission in ['contents: read', 'pull-requests: read', 'checks: write', 'actions: read']:
            self.assertIn(permission, workflow)

    def test_network_errors_do_not_expose_credentials_or_response_bodies(self):
        error = urllib.error.HTTPError('https://example.test', 403, 'forbidden', {}, None)
        with patch('urllib.request.urlopen', side_effect=error):
            with self.assertRaises(Blocked) as raised:
                JsonHTTP('https://example.test', 'private-token').call('POST', '/pages/id', {'secret': 'private-body'})
        self.assertNotIn('private-token', str(raised.exception)); self.assertNotIn('private-body', str(raised.exception))


if __name__ == '__main__': unittest.main()
