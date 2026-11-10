// Pure report adapter; supports legacy k6 summaries and the versioned metrics array.
// Missing/unknown metrics can never produce a passing result.
export function summarize(data, warmup, duration) {
  const metrics = data.metrics || Object.fromEntries((data.results?.metrics || []).map(metric => [metric.name, metric]));
  const latency = metrics.board_duration?.values;
  const failures = metrics.board_failures?.values;
  const requests = metrics.board_requests?.values?.count ?? null;
  const measured = requests > 0 && Number.isFinite(latency?.['p(95)']) && Number.isFinite(failures?.rate);
  return {
    status: measured ? 'executed' : 'no-measured-requests',
    scenario: { vus: 100, warmupSeconds: warmup, sustainedSeconds: duration, variants: 8, maxPages: 3 },
    requests, requestsPerSecond: measured ? requests/duration : null,
    successRate: measured ? 1-failures.rate : null,
    p50Ms: latency?.['p(50)'] ?? latency?.med ?? null, p95Ms: latency?.['p(95)'] ?? null, p99Ms: latency?.['p(99)'] ?? null,
    p95Below200ms: measured ? latency['p(95)'] < 200 : null,
    thresholdsPassed: measured ? latency['p(95)'] < 200 && failures.rate < 0.01 && requests >= 100*duration : null,
  };
}
