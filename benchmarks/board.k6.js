import http from 'k6/http';
import execution from 'k6/execution';
import { check, fail, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { summarize } from './result-summary.mjs';

const base = (__ENV.BASE_URL || 'http://127.0.0.1:8082').replace(/\/$/, '');
const event = __ENV.BENCHMARK_EVENT;
const warmup = 30;
const duration = 180;
const boardDuration = new Trend('board_duration', true);
const boardFailures = new Rate('board_failures');
const boardRequests = new Counter('board_requests');
export const options = {
  scenarios: {
    warmup: { executor: 'constant-vus', vus: 100, duration: `${warmup}s`, exec: 'warm', gracefulStop: '0s' },
    measured: { executor: 'constant-vus', vus: 100, duration: `${duration}s`, startTime: `${warmup}s`, exec: 'measured', gracefulStop: '10s' },
  },
  setupTimeout: '5m',
  summaryTrendStats: ['min', 'med', 'p(50)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    board_duration: ['p(95)<200'],
    board_failures: ['rate<0.01'],
    // At least one measured request per VU/second prevents a nearly idle pass.
    board_requests: [`count>=${100*duration}`],
    'checks{phase:measured}': ['rate>0.99'],
  },
  systemTags: ['status', 'method', 'name', 'scenario', 'expected_response'],
};

export function setup() {
  if (__ENV.CONFIRM_BENCHMARK !== 'teamforge-m8a-v1' || !event || !__ENV.BENCHMARK_PASSWORD)
    fail('Set CONFIRM_BENCHMARK, BENCHMARK_EVENT and the synthetic BENCHMARK_PASSWORD. Use an isolated benchmark application only.');
  const sessions = [];
  // Authentication is sequential and outside measured board timing. Respect the
  // unchanged default 30 auth attempts/minute/IP instead of weakening security.
  for (let user=1; user<=100; user++) {
    http.cookieJar().clear(base);
    const csrf = http.get(`${base}/api/v1/auth/csrf`, { tags: { name: 'auth-csrf', phase: 'setup' } });
    if (csrf.status !== 200) fail('CSRF bootstrap failed; no benchmark measurement taken.');
    const login = http.post(`${base}/api/v1/auth/login`, {
      email: `bench${String(user).padStart(5, '0')}@example.test`, password: __ENV.BENCHMARK_PASSWORD,
    }, { headers: { 'X-CSRF-TOKEN': csrf.json('token') }, tags: { name: 'auth-login', phase: 'setup' }, redirects: 0 });
    if (login.status !== 204) fail('Synthetic login failed; check password, local cookie profile and auth rate limits.');
    const cookies = http.cookieJar().cookiesForURL(base);
    if (!cookies.JSESSIONID?.length) fail('Session cookie missing.');
    sessions.push(cookies.JSESSIONID[0]);
    sleep(2.1);
  }
  http.cookieJar().clear(base);
  return { sessions };
}

const variants = [
  'sort=id&limit=50', 'sort=name&limit=25', 'sort=name&limit=50&openSeats=true',
  'sort=id&limit=25&status=INCOMPLETE', 'sort=name&limit=50&status=READY&skill=java',
  'sort=id&limit=50&skill=design', 'sort=name&limit=25&q=Circuit', 'sort=id&limit=25&openSeats=true&skill=react',
];
function browse(data, measure) {
  const variant = (__VU + __ITER) % variants.length;
  let cursor = null;
  // Follow returned keyset cursors, keeping filters/sort identical across pages.
  for (let page=0; page<3; page++) {
    const url = `${base}/api/v1/hackathons/${event}/teams?${variants[variant]}` + (cursor ? `&cursor=${encodeURIComponent(cursor)}` : '');
    const response = http.get(url, {
      cookies: { JSESSIONID: data.sessions[(__VU-1) % 100] },
      tags: { name: `board-variant-${variant}-page-${page}`, phase: measure ? 'measured' : 'warmup' }, timeout: '10s', redirects: 0,
    });
    let payload;
    try { payload = response.json(); } catch { payload = null; }
    const ok = response.status === 200 && Array.isArray(payload?.items) && payload.items.length <= 50
      && payload.items.every(team => team.memberCount >= 1 && team.openSeats >= 0 && team.memberCount + team.openSeats === team.maxSize);
    const withinMeasurement = measure && Date.now() - execution.scenario.startTime <= duration*1000;
    check(response, { 'board HTTP200 and valid capacity/pagination': () => ok }, { phase: withinMeasurement ? 'measured' : measure ? 'drain' : 'warmup' });
    if (withinMeasurement) {
      boardDuration.add(response.timings.duration);
      boardRequests.add(1);
      boardFailures.add(!ok);
    }
    if (!ok || !payload.nextCursor) break;
    cursor = payload.nextCursor;
  }
  sleep(0.2);
}
export function warm(data) { browse(data, false); }
export function measured(data) { browse(data, true); }

export function teardown(data) {
  // Invalidate only the synthetic sessions created by setup. Never print tokens.
  for (const session of data.sessions) {
    const cookies = { JSESSIONID: session };
    const csrf = http.get(`${base}/api/v1/auth/csrf`, { cookies, tags: { name: 'logout-csrf', phase: 'teardown' } });
    if (csrf.status === 200) http.post(`${base}/api/v1/auth/logout`, null, {
      cookies, headers: { 'X-CSRF-TOKEN': csrf.json('token') }, tags: { name: 'logout', phase: 'teardown' },
    });
  }
}

export function handleSummary(data) {
  const report = summarize(data, warmup, duration);
  return {
    [__ENV.SUMMARY_PATH || '.tmp/benchmark/k6-summary.json']: JSON.stringify(data, null, 2),
    [__ENV.REPORT_PATH || '.tmp/benchmark/board-results.json']: JSON.stringify(report, null, 2),
    stdout: JSON.stringify(report, null, 2) + '\n',
  };
}
