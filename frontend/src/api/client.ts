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
async function mutate(path: string, body?: string, contentType = 'application/json', method = 'POST', idempotencyKey?: string) {
  // Obtain a fresh session token for each mutation, including after login/logout rotation.
  const csrf = await request('/auth/csrf');
  if (!csrf || typeof csrf !== 'object' || !('token' in csrf) || typeof csrf.token !== 'string'
    || !('headerName' in csrf) || csrf.headerName !== 'X-CSRF-TOKEN') throw new ApiError('Refresh the page and try again.');
  const headers: Record<string, string> = { 'Content-Type': contentType, 'X-CSRF-TOKEN': csrf.token };
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
  return request(path, { method, body, headers });
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

export interface Hackathon { id: string; name: string; minSize: number; maxSize: number; active: boolean }
export interface TeamMember { userId: string; displayName: string; joinedAt: string; leader: boolean }
export interface TeamSummary {
  id: string; name: string; description: string; leaderId: string;
  status: 'INCOMPLETE' | 'READY'; minSize: number; maxSize: number; memberCount: number;
}
export interface Team extends Omit<TeamSummary, 'memberCount'> { hackathonId: string; desiredSkills: string[]; members: TeamMember[] }
export interface TeamInput { name: string; description: string; desiredSkills: string[] }
export interface AuditEvent { id: string; actorId: string; action: string; targetType: 'TEAM' | 'USER'; targetId: string; timestamp: string }
function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new ApiError('Unexpected server response. Reload and try again.');
  return value as Record<string, unknown>;
}
function text(value: unknown): string { if (typeof value !== 'string') throw new ApiError('Unexpected text in server response.'); return value; }
function count(value: unknown): number { if (typeof value !== 'number' || !Number.isInteger(value) || value < 0) throw new ApiError('Unexpected count in server response.'); return value; }
function list(value: unknown): unknown[] { if (!Array.isArray(value)) throw new ApiError('Unexpected list in server response.'); return value; }
function readiness(value: unknown): TeamSummary['status'] { if (value !== 'INCOMPLETE' && value !== 'READY') throw new ApiError('Unexpected team readiness.'); return value; }
function teamResponse(value: unknown): Team {
  const team = object(value);
  return { id: text(team.id), hackathonId: text(team.hackathonId), name: text(team.name), description: text(team.description),
    leaderId: text(team.leaderId), status: readiness(team.status), minSize: count(team.minSize), maxSize: count(team.maxSize),
    desiredSkills: list(team.desiredSkills).map(text), members: list(team.members).map(value => {
      const member = object(value); if (typeof member.leader !== 'boolean') throw new ApiError('Unexpected member role.');
      return { userId: text(member.userId), displayName: text(member.displayName), joinedAt: text(member.joinedAt), leader: member.leader };
    }) };
}
export async function listHackathons(): Promise<Hackathon[]> {
  return list(object(await request('/hackathons')).items).map(value => {
    const event = object(value); if (typeof event.active !== 'boolean') throw new ApiError('Unexpected event state.');
    return { id: text(event.id), name: text(event.name), minSize: count(event.minSize), maxSize: count(event.maxSize), active: event.active };
  });
}
export async function listTeams(event: string, cursor?: string): Promise<{ items: TeamSummary[]; nextCursor: string | null }> {
  const result = object(await request('/hackathons/' + encodeURIComponent(event) + '/teams?limit=50' + (cursor ? '&cursor=' + encodeURIComponent(cursor) : '')));
  return { items: list(result.items).map(value => {
    const team = object(value); return { id: text(team.id), name: text(team.name), description: text(team.description), leaderId: text(team.leaderId),
      status: readiness(team.status), minSize: count(team.minSize), maxSize: count(team.maxSize), memberCount: count(team.memberCount) };
  }), nextCursor: result.nextCursor === null ? null : text(result.nextCursor) };
}
export async function myTeam(event: string): Promise<Team | null> {
  const result = await request('/hackathons/' + encodeURIComponent(event) + '/me/team');
  return result === undefined ? null : teamResponse(result);
}
export async function getTeam(id: string): Promise<Team> { return teamResponse(await request('/teams/' + encodeURIComponent(id))); }
export async function createTeam(event: string, input: TeamInput, idempotencyKey: string): Promise<Team> {
  return teamResponse(await mutate('/hackathons/' + encodeURIComponent(event) + '/teams', JSON.stringify(input), 'application/json', 'POST', idempotencyKey));
}
export async function updateTeam(id: string, input: Partial<TeamInput>): Promise<Team> {
  return teamResponse(await mutate('/teams/' + encodeURIComponent(id), JSON.stringify(input), 'application/json', 'PATCH'));
}
export async function leaveTeam(id: string): Promise<{ deleted: boolean; leaderId: string | null }> {
  const response = object(await mutate('/teams/' + encodeURIComponent(id) + '/members/me', undefined, 'application/json', 'DELETE'));
  if (typeof response.deleted !== 'boolean') throw new ApiError('Unexpected departure response. Refresh your dashboard.');
  return { deleted: response.deleted, leaderId: response.leaderId === null ? null : text(response.leaderId) };
}
export async function removeTeamMember(team: string, member: string): Promise<void> {
  await mutate('/teams/' + encodeURIComponent(team) + '/members/' + encodeURIComponent(member), undefined, 'application/json', 'DELETE');
}
export async function teamAudit(id: string, offset = 0): Promise<AuditEvent[]> {
  return list(object(await request('/teams/' + encodeURIComponent(id) + '/audit-events?limit=50&offset=' + offset)).items).map(value => {
    const event = object(value); if (event.targetType !== 'TEAM' && event.targetType !== 'USER') throw new ApiError('Unexpected audit target.');
    return { id: text(event.id), actorId: text(event.actorId), action: text(event.action), targetType: event.targetType, targetId: text(event.targetId), timestamp: text(event.timestamp) };
  });
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
