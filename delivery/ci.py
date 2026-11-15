"""Trusted-main GitHub event adapter; writes check receipts only, never executes PR code or writes Notion."""
import json
import os
from pathlib import Path
import re
import sys
from delivery.coordinator import Blocked, Coordinator, GitHub, definition_path, validate_definition, story_tags


def event_check(git, event_name, event):
    service = Coordinator(git)
    if event_name == 'workflow_run':
        run = event['workflow_run']
        if run['event'] == 'pull_request':
            matches = [p for p in git.pulls() if p['state'] == 'open' and p['head']['sha'] == run['head_sha']]
            if len(matches) != 1: return {'skipped': 'No unique current PR head for completed CI; stale run ignored'}
            return event_check(git, 'workflow_dispatch', {'inputs': {'pr_number': matches[0]['number']}})
        if run['head_branch'] != 'main' or run['event'] != 'push':
            return {'skipped': 'Only main push CI is eligible for integration verification'}
        if run['conclusion'] != 'success':
            raise Blocked('Main CI failed; stories cannot be marked Done')
        return audit(service, run['head_sha'])
    if event_name == 'workflow_dispatch':
        pr = git.pr(int(event['inputs']['pr_number']))
    elif event_name == 'issue_comment':
        if not event.get('issue', {}).get('pull_request'):
            return {'skipped': 'Comment is not on a pull request'}
        pr = git.pr(event['issue']['number'])
    else:
        pr = event.get('pull_request')
    if not pr:
        raise Blocked('No pull request in event')
    pr = git.pr(pr['number'])  # Event payloads may lag newer edits or pushes.
    tags = story_tags(pr.get('body'))
    if len(tags) != 1:
        raise Blocked('Exactly one canonical <!-- gbd:GBD-number --> marker is required')
    story = validate_definition(git.file(definition_path(tags[0]), pr['head']['sha']))
    if event_name in ('pull_request', 'pull_request_target') and event.get('action') == 'closed':
        if not pr.get('merged'):
            return {'closed_unmerged': pr['number'], 'note': 'Claim retained; explicit recovery needed'}
        return service.verify(story, pr['number'])
    return service.pr_readiness(story, pr['number'])


def audit(service, sha):
    receipts = []
    for path in sorted(Path('.delivery/stories').glob('GBD-*.json')):
        story = validate_definition(json.loads(path.read_text()))
        prs = [service.git.pr(story['legacy_pr'])] if story.get('legacy_pr') else service.matching_prs(story)
        for pr in prs:
            if pr.get('merged_at') or pr.get('merged'):
                receipts.append(service.verify(story, pr['number'], sha))
    return {'verified_stories': receipts, 'notion_updated': False}


def publish_readiness(git, result):
    """A distinct current-head check stays in progress while review/CI is legitimately pending.

    The workflow's successful evidence collection is not a green merge-readiness claim.
    Only trusted-main code invokes this API; no PR head code runs with this token.
    """
    if 'head_sha' not in result: return
    sha = result['head_sha']
    ready = result['review_ready']
    failed = bool(result.get('blocked'))
    body = {'name': 'Story merge readiness', 'head_sha': sha,
            'status': 'completed' if ready or failed else 'in_progress',
            'output': {'title': 'Readiness blocked' if failed else 'Ready for human review' if ready else 'Awaiting required evidence',
                       'summary': result['blocked'] if failed else '\n'.join(result['pending']) if not ready else 'Current-head CI, identity, Copilot count and resolved threads verified. Independent GPT receipt remains owner-verified. Human merge only.'}}
    if ready or failed: body['conclusion'] = 'failure' if failed else 'success'
    # Always re-read the actual PR head before attaching a readiness result.
    if git.pr(result['pr'])['head']['sha'] != sha: raise Blocked('Head changed before readiness publication; refresh required')
    existing = git.pages(f'/commits/{sha}/check-runs', 'check_runs')
    ours = next((c for c in existing if c['name'] == body['name'] and c.get('app', {}).get('slug') == 'github-actions'), None)
    if ours:
        body.pop('head_sha')
        git.api('PATCH', '/check-runs/'+str(ours['id']), body)
    else: git.api('POST', '/check-runs', body)


def main():
    try:
        event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
        git = GitHub(os.environ['GITHUB_REPOSITORY'], os.environ['GITHUB_TOKEN'])
        checked = event_check(git, os.environ['GITHUB_EVENT_NAME'], event)
        publish_readiness(git, checked)
        result = {'ok': True, 'result': checked}
        exit_code = 0
    except (Blocked, KeyError, ValueError, OSError) as error:
        result, exit_code = {'ok': False, 'blocked': str(error)}, 1
        # A failed refresh must replace any old green/pending check on the current head.
        try:
            number = event.get('pull_request', {}).get('number') or event.get('issue', {}).get('number') or event.get('inputs', {}).get('pr_number')
            if os.environ['GITHUB_EVENT_NAME'] == 'workflow_run' and event['workflow_run']['event'] == 'pull_request':
                matches = [p for p in git.pulls() if p['state'] == 'open' and p['head']['sha'] == event['workflow_run']['head_sha']]
                number = matches[0]['number'] if len(matches) == 1 else None
            if number:
                pr = git.pr(int(number))
                publish_readiness(git, {'pr': pr['number'], 'head_sha': pr['head']['sha'], 'review_ready': False, 'blocked': str(error)})
        except (Blocked, KeyError, ValueError, OSError):
            pass  # Original failure remains visible; never convert an error to readiness.
    output = json.dumps(result, indent=2)
    Path('delivery-receipt.json').write_text(output + '\n')
    print(output)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
            summary.write('## Story delivery verification\n\n```json\n' + output.replace('```', '') + '\n```\n')
    return exit_code


if __name__ == '__main__': sys.exit(main())
