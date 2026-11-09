export interface HealthResponse {
  status: 'UP';
}

export async function getHealth(signal?: AbortSignal): Promise<HealthResponse> {
  const response = await fetch('/api/v1/health', {
    signal,
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) throw new Error('Backend health request failed.');
  const payload: unknown = await response.json();
  if (typeof payload !== 'object' || payload === null ||
      !('status' in payload) || payload.status !== 'UP') {
    throw new Error('Unexpected backend health response.');
  }
  return { status: 'UP' };
}
