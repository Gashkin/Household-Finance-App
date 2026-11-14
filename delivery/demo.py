"""Credential-free stateful demo; this never calls GitHub or Notion."""
from concurrent.futures import ThreadPoolExecutor
import json
import threading
from delivery.coordinator import Blocked, Coordinator
from delivery.tests.test_coordinator import MemoryGit, MemoryNotion, STORY


def main():
    git, notion = MemoryGit(), MemoryNotion()
    notion.ready_barrier = threading.Barrier(2)
    def worker(token):
        try: return Coordinator(git, notion).claim(STORY, token)['token']
        except Blocked: return 'rejected'
    with ThreadPoolExecutor(2) as workers:
        claims = list(workers.map(worker, ['demo-A', 'demo-B']))
    owner = next(token for token in claims if token != 'rejected')
    notion.ready_barrier = None
    coordinator = Coordinator(git, notion)
    pr = coordinator.publish(STORY, owner, 'Demonstrable capability', 'One story')
    git.human_merge(pr['number'])  # Simulator only; production coordinator has no merge operation.
    receipt = coordinator.finish(STORY, owner, pr['number'])
    print(json.dumps({'simulation': True, 'two_workers': claims, 'pr_count': len(git.prs), 'notion_status': notion.page['status'], 'receipt': receipt}, indent=2))


if __name__ == '__main__': main()
