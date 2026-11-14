"""Real HTTP integration against a local stateful GitHub/Notion protocol simulator."""
import base64
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import threading
import unittest
from urllib.parse import parse_qs, unquote, urlsplit
from delivery.coordinator import Blocked, Coordinator, GitHub, JsonHTTP, Notion
from delivery.tests.test_coordinator import MemoryGit, MemoryNotion, STORY


class HTTPIntegrationTests(unittest.TestCase):
    def test_two_workers_across_http_clients_get_one_exclusive_claim(self):
        git, notion = MemoryGit(), MemoryNotion()
        barrier, counter_lock = threading.Barrier(2), threading.Lock()
        reads = 0
        trees = {}

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args): pass
            def do_GET(self): self.dispatch('GET')
            def do_POST(self): self.dispatch('POST')
            def do_PATCH(self): self.dispatch('PATCH')
            def dispatch(self, method):
                nonlocal reads
                url = urlsplit(self.path); path = unquote(url.path)
                body = json.loads(self.rfile.read(int(self.headers.get('Content-Length', '0'))) or '{}')
                try:
                    if path.startswith('/notion/pages/'):
                        if method == 'PATCH':
                            values = {}
                            for key, prop in body['properties'].items():
                                name = next(k for k, v in Notion.fields.items() if v == key)
                                if name == 'status': value = prop['status']['name']
                                elif name == 'pr': value = prop['url']
                                elif name == 'claimed_at': value = prop['date']['start']
                                else: value = ''.join(t['text']['content'] for t in prop['rich_text'])
                                values[name] = value
                            notion.update(STORY['page_id'], **values)
                        page = notion.get(STORY['page_id'])
                        if method == 'GET':
                            with counter_lock: reads += 1; initial = reads <= 2
                            if initial: barrier.wait(timeout=5)
                        props = {'Work ID': {'unique_id': {'prefix': 'GBD', 'number': 10}}}
                        for key, name in Notion.fields.items():
                            value = page.get(key)
                            if key == 'status': props[name] = {'status': {'name': value}}
                            elif key == 'pr': props[name] = {'url': value}
                            elif key == 'claimed_at': props[name] = {'date': {'start': value} if value else None}
                            else: props[name] = {'rich_text': [{'plain_text': value}] if value else []}
                        response = {'properties': props}
                    else:
                        suffix = path.removeprefix('/github/repos/owner/repo')
                        if suffix.startswith('/git/ref/heads/'):
                            response = {'object': {'sha': git.head(suffix.removeprefix('/git/ref/heads/'))}}
                        elif suffix == '/pulls': response = git.pulls()
                        elif suffix.startswith('/git/commits/'):
                            response = {'tree': {'sha': suffix.split('/')[-1]}}
                        elif suffix == '/git/trees':
                            sha = 'tree-' + str(len(trees))
                            trees[sha] = body
                            response = {'sha': sha}
                        elif suffix == '/git/commits':
                            tree = trees[body['tree']]
                            entry = tree['tree'][0]
                            sha = git.commit_file(body['parents'][0], entry['path'], json.loads(entry['content']), body['message'])
                            response = {'sha': sha}
                        elif suffix == '/git/refs':
                            git.create_ref(body['ref'].removeprefix('refs/heads/'), body['sha'])
                            response = {'ref': body['ref']}
                        elif suffix.startswith('/contents/'):
                            text = git.raw_file(suffix.removeprefix('/contents/'), parse_qs(url.query)['ref'][0])
                            response = {'encoding': 'base64', 'content': base64.b64encode(text.encode()).decode()}
                        else: raise AssertionError((method, suffix))
                    code = 200
                except Blocked:
                    response, code = {'message': 'Reference already exists'}, 422
                raw = json.dumps(response).encode()
                self.send_response(code); self.send_header('Content-Type', 'application/json'); self.send_header('Content-Length', str(len(raw))); self.end_headers(); self.wfile.write(raw)

        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
        root = f'http://127.0.0.1:{server.server_port}'
        def worker(token):
            service = Coordinator(GitHub('owner/repo', http=JsonHTTP(root + '/github')), Notion('unused', http=JsonHTTP(root + '/notion')))
            try: return service.claim(STORY, token)['token']
            except Blocked: return 'rejected'
        try:
            with ThreadPoolExecutor(2) as pool: results = list(pool.map(worker, ['http-A', 'http-B']))
            self.assertEqual(results.count('rejected'), 1)
            owner = next(r for r in results if r != 'rejected')
            self.assertEqual(notion.page['token'], owner)
            self.assertEqual(notion.page['status'], 'In Progress')
        finally:
            server.shutdown(); server.server_close(); thread.join(timeout=5)


if __name__ == '__main__': unittest.main()
