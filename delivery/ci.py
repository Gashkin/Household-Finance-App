"""Read-only GitHub event adapter; never executes PR-provided commands or writes Notion."""
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
    tags = story_tags(pr.get('body'))
    if len(tags) != 1:
        raise Blocked('Exactly one canonical <!-- gbd:GBD-number --> marker is required')
    story = validate_definition(git.file(definition_path(tags[0]), pr['head']['sha']))
    if event_name in ('pull_request', 'pull_request_target') and event.get('action') == 'closed':
        if not pr.get('merged'):
            return {'closed_unmerged': pr['number'], 'note': 'Claim retained; explicit recovery needed'}
        return service.verify(story, pr['number'])
    return {'pr': pr['number'], 'review_ready': bool(service.pr_gate(story, pr['number']))}


def audit(service, sha):
    receipts = []
    for path in sorted(Path('.delivery/stories').glob('GBD-*.json')):
        story = validate_definition(json.loads(path.read_text()))
        prs = [service.git.pr(story['legacy_pr'])] if story.get('legacy_pr') else service.matching_prs(story)
        for pr in prs:
            if pr.get('merged_at') or pr.get('merged'):
                receipts.append(service.verify(story, pr['number'], sha))
    return {'verified_stories': receipts, 'notion_updated': False}


def main():
    try:
        event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
        git = GitHub(os.environ['GITHUB_REPOSITORY'], os.environ['GITHUB_TOKEN'])
        result = {'ok': True, 'result': event_check(git, os.environ['GITHUB_EVENT_NAME'], event)}
        exit_code = 0
    except (Blocked, KeyError, ValueError, OSError) as error:
        result, exit_code = {'ok': False, 'blocked': str(error)}, 1
    output = json.dumps(result, indent=2)
    Path('delivery-receipt.json').write_text(output + '\n')
    print(output)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
            summary.write('## Story delivery verification\n\n```json\n' + output.replace('```', '') + '\n```\n')
    return exit_code


if __name__ == '__main__': sys.exit(main())
