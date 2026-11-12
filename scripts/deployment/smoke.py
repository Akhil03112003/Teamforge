"""HTTPS deployment smoke; default is public reads, authenticated writes are opt-in.

Credentials: SMOKE_EMAIL/SMOKE_PASSWORD environment only. Never disables TLS checks.
"""
import argparse
import http.cookiejar
from html.parser import HTMLParser
import json
from pathlib import Path
import os
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


class Assets(HTMLParser):
    def __init__(self):
        super().__init__(); self.paths = []

    def handle_starttag(self, tag, attributes):
        data = dict(attributes)
        value = data.get('src') if tag == 'script' else data.get('href') if tag == 'link' and data.get('rel') == 'stylesheet' else None
        if value:
            self.paths.append(value)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--ca-file')
    parser.add_argument('--authenticated', action='store_true')
    parser.add_argument('--event')
    parser.add_argument('--write-fixture', action='store_true', help='Explicitly create/leave one synthetic team; immutable audit remains')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    parsed = urllib.parse.urlsplit(args.base_url)
    if parsed.scheme != 'https' or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ('', '/'):
        parser.error('Use an HTTPS origin without URL credentials/path/query/fragment.')
    if args.write_fixture and not args.authenticated:
        parser.error('--write-fixture requires --authenticated and a dedicated smoke account.')
    if args.authenticated and (not args.event or not os.getenv('SMOKE_EMAIL') or not os.getenv('SMOKE_PASSWORD')):
        parser.error('Set SMOKE_EMAIL/SMOKE_PASSWORD privately and supply --event UUID.')
    if args.event:
        uuid.UUID(args.event)
    base = args.base_url.rstrip('/')
    context = ssl.create_default_context(cafile=args.ca_file)
    opener = urllib.request.build_opener(urllib.request.HTTPSHandler(context=context), urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    result = {'baseUrl': base, 'tlsVerification': True, 'writeFixture': args.write_fixture, 'checks': []}
    csrf = None; team = None; stream = None

    def call(method, path, body=None, protect=False, form=False, expected=200, key=None):
        headers = {}
        if protect:
            headers['X-CSRF-TOKEN'] = csrf
        if key:
            headers['Idempotency-Key'] = key
        data = None
        if body is not None:
            data = (urllib.parse.urlencode(body) if form else json.dumps(body)).encode()
            headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
        request = urllib.request.Request(base+path, data=data, headers=headers, method=method)
        try:
            response = opener.open(request, timeout=35)
        except urllib.error.HTTPError as failure:
            response = failure
        with response:
            status = response.status; response_headers = response.headers; payload = response.read()
        assert status == expected, f'{method} {path.split("?")[0]} status {status}, expected {expected}'
        return payload, response_headers

    def token():
        nonlocal csrf
        payload, headers = call('GET', '/api/v1/auth/csrf')
        csrf = json.loads(payload)['token']
        return headers

    started = time.monotonic()
    try:
        for path in ['/api/v1/health', '/actuator/health/liveness', '/actuator/health/readiness']:
            body, _ = call('GET', path); assert json.loads(body)['status'] == 'UP'
        result['checks'].append('public health/liveness/readiness')
        body, headers = call('GET', '/')
        assert 'default-src' in headers.get('Content-Security-Policy', '')
        assert headers.get('Strict-Transport-Security'), 'HTTPS edge/application must provide HSTS'
        assets = Assets(); assets.feed(body.decode())
        assert len(assets.paths) >= 2, 'Production HTML must contain JS/CSS assets'
        for path in assets.paths:
            target = urllib.parse.urlsplit(urllib.parse.urljoin(base+'/', path))
            assert (target.scheme, target.netloc) == (parsed.scheme, parsed.netloc), 'Asset escaped application origin'
            call('GET', target.path)
        result['checks'].append('same-origin HTML/JS/CSS and browser policy')
        headers = token()
        cookie = headers.get('Set-Cookie', '').lower()
        assert 'secure' in cookie and 'httponly' in cookie and 'samesite=lax' in cookie
        result['checks'].append('Secure/HttpOnly/SameSite session cookie')
        if args.authenticated:
            call('POST', '/api/v1/auth/login', {'email': os.environ['SMOKE_EMAIL'], 'password': os.environ['SMOKE_PASSWORD']}, protect=True, form=True, expected=204)
            token(); call('GET', '/api/v1/me')
            call('POST', '/api/v1/auth/logout', expected=403)
            call('GET', '/api/v1/me')
            result['checks'].append('session login/restoration and mutation CSRF rejection')
            board = '/api/v1/hackathons/'+args.event+'/teams?sort=name&limit=5'
            body, _ = call('GET', board); assert isinstance(json.loads(body)['items'], list)
            stream = opener.open(urllib.request.Request(base+'/api/v1/hackathons/'+args.event+'/events', headers={'Accept': 'text/event-stream'}), timeout=35)
            assert stream.status == 200 and stream.headers.get('X-Accel-Buffering') == 'no'
            def event(expected, seconds=35):
                deadline = time.monotonic()+seconds
                while time.monotonic() < deadline:
                    raw = stream.readline()
                    assert raw, 'Live connection closed before expected event'
                    line = raw.decode().strip()
                    if line == expected:
                        return time.monotonic()
                raise AssertionError('Expected live event/heartbeat not received within timeout')
            event('event:ready')
            if args.write_fixture:
                payload, _ = call('POST', '/api/v1/hackathons/'+args.event+'/teams', {'name': 'Deployment smoke '+str(uuid.uuid4())}, protect=True, expected=201, key=str(uuid.uuid4()))
                team = json.loads(payload)['id']; event('event:invalidate')
                call('GET', '/api/v1/teams/'+team)
                result['checks'].append('committed team mutation -> SSE invalidation -> REST')
            first = event(':heartbeat'); second = event(':heartbeat')
            result['heartbeatGapSeconds'] = round(second-first, 3)
            assert second-first >= 1, 'Heartbeats arrived together; inspect proxy buffering'
            stream.close(); stream = None
            result['checks'].append('same-origin SSE ready/two spaced heartbeats, no buffering')
            if team:
                payload, _ = call('DELETE', '/api/v1/teams/'+team+'/members/me', protect=True)
                assert json.loads(payload)['deleted']; team = None
                result['checks'].append('owned final-member team cleanup')
            token(); call('POST', '/api/v1/auth/logout', protect=True, expected=204)
            call('GET', '/api/v1/me', expected=401)
            result['checks'].append('logout invalidation')
        result['status'] = 'passed'
    except Exception as failure:
        result['status'] = 'failed'; result['failure'] = str(failure)
    finally:
        if stream:
            stream.close()
        if team:
            try:
                call('DELETE', '/api/v1/teams/'+team+'/members/me', protect=True)
            except Exception:
                result['fixtureCleanup'] = 'failed; inspect owned fixture privately'
        result['elapsedSeconds'] = round(time.monotonic()-started, 3)
    output = json.dumps(result, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output+'\n', encoding='utf-8')
    print(output)
    return 0 if result['status'] == 'passed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
