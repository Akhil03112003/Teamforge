export interface HealthResponse {
  status: 'UP';
}

export type Day = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';
export interface AvailabilityWindow { day: Day; start: string; end: string }
export interface Profile {
  displayName: string;
  timeZone: string;
  skills: string[];
  collaborationWindows: AvailabilityWindow[];
}
export interface Participant { id: string; email: string; profile: Profile }
export class ApiError extends Error {
  constructor(message: string, public status = 0, public fieldErrors: Record<string, string> = {}) { super(message); }
  static from(reason: unknown) {
    return reason instanceof ApiError ? reason : new ApiError('Could not reach TeamForge. Check your connection and try again.');
  }
}
async function request(path: string, init: RequestInit = {}): Promise<unknown> {
  const response = await fetch('/api/v1' + path, { ...init, credentials: 'same-origin', cache: 'no-store' });
  if (!response.ok) {
    const error: unknown = await response.json().catch(() => null);
    let message = 'The request failed. Please try again.';
    const fields: Record<string, string> = {};
    if (error && typeof error === 'object') {
      if ('message' in error && typeof error.message === 'string') message = error.message;
      if ('fieldErrors' in error && error.fieldErrors && typeof error.fieldErrors === 'object') {
        for (const [key, value] of Object.entries(error.fieldErrors)) if (typeof value === 'string') fields[key] = value;
      }
    }
    throw new ApiError(message, response.status, fields);
  }
  return response.status === 204 ? undefined : response.json();
}
async function mutate(path: string, body?: string, contentType = 'application/json', method = 'POST') {
  // Obtain a fresh session token for each mutation, including after login/logout rotation.
  const csrf = await request('/auth/csrf');
  if (!csrf || typeof csrf !== 'object' || !('token' in csrf) || typeof csrf.token !== 'string'
    || !('headerName' in csrf) || csrf.headerName !== 'X-CSRF-TOKEN') throw new ApiError('Refresh the page and try again.');
  return request(path, { method, body, headers: { 'Content-Type': contentType, 'X-CSRF-TOKEN': csrf.token } });
}
function profileResponse(value: unknown): Profile {
  if (!value || typeof value !== 'object' || !('displayName' in value) || typeof value.displayName !== 'string'
    || !('timeZone' in value) || typeof value.timeZone !== 'string' || !('skills' in value) || !Array.isArray(value.skills)
    || !value.skills.every(skill => typeof skill === 'string') || !('collaborationWindows' in value)
    || !Array.isArray(value.collaborationWindows)) throw new ApiError('Unexpected profile response. Reload the page.');
  const days = new Set(['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY']);
  const windows: AvailabilityWindow[] = value.collaborationWindows.map((slot: unknown) => {
    if (!slot || typeof slot !== 'object' || !('day' in slot) || typeof slot.day !== 'string' || !days.has(slot.day)
      || !('start' in slot) || typeof slot.start !== 'string' || !('end' in slot) || typeof slot.end !== 'string')
      throw new ApiError('Unexpected availability response. Reload the page.');
    return { day: slot.day as Day, start: slot.start, end: slot.end };
  });
  return { displayName: value.displayName, timeZone: value.timeZone, skills: value.skills, collaborationWindows: windows };
}
function participantResponse(value: unknown): Participant {
  if (!value || typeof value !== 'object' || !('id' in value) || typeof value.id !== 'string'
    || !('email' in value) || typeof value.email !== 'string' || !('profile' in value))
    throw new ApiError('Unexpected session response. Reload the page.');
  return { id: value.id, email: value.email, profile: profileResponse(value.profile) };
}
export async function currentUser(signal?: AbortSignal): Promise<Participant> {
  return participantResponse(await request('/me', { signal }));
}
export async function register(input: { email: string; password: string; displayName: string }): Promise<void> {
  await mutate('/auth/register', JSON.stringify(input));
}
export async function login(email: string, password: string): Promise<Participant> {
  await mutate('/auth/login', new URLSearchParams({ email, password }).toString(), 'application/x-www-form-urlencoded');
  return currentUser();
}
export async function logout(): Promise<void> { await mutate('/auth/logout'); }
export async function updateProfile(profile: Partial<Profile>): Promise<Profile> {
  return profileResponse(await mutate('/me/profile', JSON.stringify(profile), 'application/json', 'PATCH'));
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
