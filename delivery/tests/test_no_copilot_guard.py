"""Provider review history must not replace identity, thread or CI evidence."""
from copy import deepcopy
import unittest
from unittest.mock import Mock
from delivery.coordinator import Blocked, Coordinator, validate_definition
from delivery.tests.test_coordinator import MemoryGit, MemoryNotion, STORY


class NoCopilotGuardTests(unittest.TestCase):
    def setUp(self):
        self.git, self.notion = MemoryGit(), MemoryNotion()
        self.service = Coordinator(self.git, self.notion)
        self.service.claim(STORY, 'owner-A')
        self.pr = self.service.publish(STORY, 'owner-A', 'Feature', 'A complete outcome')

    def forbid_provider_queries(self):
        self.git.reviews = Mock(side_effect=AssertionError('Provider review history must not be queried'))

    def test_readiness_with_no_copilot_does_not_query_review_history(self):
        self.git.review_items = []
        self.forbid_provider_queries()
        self.assertTrue(self.service.pr_readiness(STORY, 1)['review_ready'])
        self.assertEqual(self.service.pr_gate(STORY, 1)['number'], 1)
        self.git.reviews.assert_not_called()

    def test_historical_multiple_or_stale_copilot_reviews_do_not_block_integration(self):
        self.git.review_items = [
            {'id': i, 'state': 'CHANGES_REQUESTED', 'commit_id': 'historical-head',
             'user': {'login': 'copilot-pull-request-reviewer[bot]', 'type': 'Bot'}}
            for i in range(3)]
        self.forbid_provider_queries()
        self.git.human_merge(1)
        receipt = self.service.finish(STORY, 'owner-A', 1)
        self.assertTrue(receipt['eligible_for_done'])
        self.assertTrue(receipt['review_threads_resolved'])
        self.assertNotIn('copilot_review', receipt)
        self.assertEqual(self.notion.page['status'], 'Done')

    def test_legacy_count_configuration_and_delta_receipts_cannot_control_readiness(self):
        self.forbid_provider_queries()
        for value in [None, {'count': 2}, {'count': 99}, 'historical data']:
            story = {**deepcopy(STORY), 'copilot_review_exception': value}
            validate_definition(story)
            for receipt in ['', '\n<!-- gbd-owned-verification invalid-json -->',
                            '\n<!-- gbd-owned-verification {"head_sha":"stale"} -->']:
                self.git.prs[0]['body'] = self.pr['body'] + receipt
                result = self.service.pr_readiness(story, 1)
                self.assertTrue(result['review_ready'])
                self.assertNotIn('copilot_reviewed_sha', result)
                self.assertNotIn('owned_delta_verified', result)

    def test_unresolved_threads_still_prevent_completion_without_partial_notion_write(self):
        self.forbid_provider_queries()
        self.git.thread_items = [{'isResolved': False}]
        self.assertFalse(self.service.pr_readiness(STORY, 1)['review_ready'])
        with self.assertRaisesRegex(Blocked, 'conversations'):
            self.service.pr_gate(STORY, 1)
        self.git.human_merge(1)
        before = deepcopy(self.notion.page)
        with self.assertRaisesRegex(Blocked, 'conversations'):
            self.service.finish(STORY, 'owner-A', 1)
        self.assertEqual(before, self.notion.page)

    def test_failed_or_missing_current_ci_is_not_bypassed_by_old_receipt(self):
        self.forbid_provider_queries()
        self.git.prs[0]['body'] += '\n<!-- gbd-owned-verification {"head_sha":"old"} -->'
        self.git.check = None
        self.assertFalse(self.service.pr_readiness(STORY, 1)['review_ready'])
        self.git.check = {'status': 'completed', 'conclusion': 'failure'}
        with self.assertRaisesRegex(Blocked, 'CI failed'):
            self.service.pr_readiness(STORY, 1)
        self.git.human_merge(1)
        with self.assertRaisesRegex(Blocked, 'build-and-test'):
            self.service.verify(STORY, 1)

    def test_thread_lookup_failure_fails_closed(self):
        self.forbid_provider_queries()
        self.git.threads = Mock(side_effect=Blocked('Thread API unavailable'))
        with self.assertRaisesRegex(Blocked, 'unavailable'):
            self.service.pr_readiness(STORY, 1)
        self.git.human_merge(1)
        with self.assertRaisesRegex(Blocked, 'unavailable'):
            self.service.finish(STORY, 'owner-A', 1)
        self.assertEqual(self.notion.page['status'], 'In Review')
