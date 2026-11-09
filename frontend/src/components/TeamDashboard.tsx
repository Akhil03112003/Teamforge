import { useEffect, useRef, useState, type FormEvent } from 'react';
import AdmissionPanel from './AdmissionPanel';
import Suggestions from './Suggestions';
import { useLiveEvent } from './useLiveEvent';
import { ApiError, createTeam, getTeam, leaveTeam, listHackathons, listTeams, myTeam, removeTeamMember, teamAudit, updateTeam,
  type AuditEvent, type Hackathon, type Participant, type Team, type TeamInput, type TeamSummary } from '../api/client';

function TeamForm({ initial, busy, submit }: { initial?: Team; busy: boolean; submit: (input: TeamInput) => void }) {
  const [name, setName] = useState(initial?.name ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [skills, setSkills] = useState(initial?.desiredSkills.join(', ') ?? '');
  const prefix = initial ? 'edit-team-' : 'create-team-';
  function save(event: FormEvent) { event.preventDefault(); submit({ name: name.trim(), description,
    desiredSkills: skills.split(',').map(skill => skill.trim()).filter(Boolean) }); }
  return <form onSubmit={save} aria-busy={busy}><fieldset disabled={busy}>
    <legend>{initial ? 'Edit team details' : 'Start a team'}</legend>
    <div className="field"><label htmlFor={prefix + 'name'}>Team name</label><input id={prefix + 'name'} required maxLength={120} value={name} onChange={event => setName(event.target.value)} /></div>
    <div className="field"><label htmlFor={prefix + 'description'}>Description</label><textarea id={prefix + 'description'} maxLength={2000} rows={3} value={description} onChange={event => setDescription(event.target.value)} /></div>
    <div className="field"><label htmlFor={prefix + 'skills'}>Desired skills</label><input id={prefix + 'skills'} value={skills} maxLength={820} onChange={event => setSkills(event.target.value)} aria-describedby={prefix + 'hint'} />
      <p id={prefix + 'hint'} className="note">Up to 20 comma-separated skill tags. Each tag can have up to 40 characters.</p></div>
    <button type="submit">{busy ? 'Saving…' : initial ? 'Save team details' : 'Create team'}</button>
  </fieldset></form>;
}

export default function TeamDashboard({ user, onSessionExpired }: { user: Participant; onSessionExpired: () => void }) {
  const [events, setEvents] = useState<Hackathon[]>([]);
  const [eventId, setEventId] = useState('');
  const [board, setBoard] = useState<TeamSummary[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [own, setOwn] = useState<Team | null>(null);
  const [selected, setSelected] = useState<Team | null>(null);
  const [history, setHistory] = useState<{ teamId: string; items: AuditEvent[] } | null>(null);
  const [moreHistory, setMoreHistory] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [revision, setRevision] = useState(0);
  const [skillDraft, setSkillDraft] = useState('');
  const [filters, setFilters] = useState<{ skill?: string; openSeats?: boolean; status?: string; sort?: 'id' | 'name' }>({ sort: 'id' });
  const selectedId = useRef<string | null>(null);
  const loadedEvent = useRef('');
  const liveStatus = useLiveEvent(eventId, () => setRevision(value => value + 1), onSessionExpired);
  const [confirmation, setConfirmation] = useState<{ type: 'leave' | 'remove'; userId?: string } | null>(null);
  const summary = useRef<HTMLDivElement>(null);
  const pendingCreate = useRef<{ fingerprint: string; key: string } | null>(null);
  useEffect(() => { if (error) summary.current?.focus(); }, [error]);
  function failure(reason: unknown) {
    const problem = ApiError.from(reason);
    if (problem.status === 401) onSessionExpired(); else setError(problem.message + ' ' + Object.entries(problem.fieldErrors).map(([field, message]) => field + ': ' + message).join(' '));
  }
  useEffect(() => {
    let active = true;
    listHackathons().then(items => { if (active) { setEvents(items); setEventId(items[0]?.id ?? ''); if (!items.length) setLoading(false); } })
      .catch(reason => { if (active) { failure(reason); setLoading(false); } });
    return () => { active = false; };
    // The dashboard is remounted when the authenticated participant changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  useEffect(() => {
    if (!eventId) return;
    let active = true; if (loadedEvent.current !== eventId) setLoading(true); setError(''); setConfirmation(null);
    Promise.all([myTeam(eventId), listTeams(eventId, undefined, filters)]).then(async ([team, result]) => {
      if (!active) return;
      let viewed = team;
      if (selectedId.current && selectedId.current !== team?.id) {
        try { viewed = await getTeam(selectedId.current); } catch (reason) { if (ApiError.from(reason).status !== 404) throw reason; selectedId.current = null; }
      }
      if (!active) return;
      setOwn(team); setSelected(viewed); setBoard(result.items); setCursor(result.nextCursor);
      // Authoritative membership also confirms a creation whose HTTP response was lost.
      if (team) pendingCreate.current = null;
      loadedEvent.current = eventId;
      if (team) {
        const items = await teamAudit(team.id);
        if (active) { setHistory({ teamId: team.id, items }); setMoreHistory(items.length === 50); }
      }
    }).catch(reason => { if (active) failure(reason); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId, revision, filters]);

  async function act(operation: () => Promise<void>) {
    setBusy(true); setError('');
    try { await operation(); } catch (reason) { failure(reason); } finally { setBusy(false); }
  }
  const event = events.find(item => item.id === eventId);
  const isMember = selected?.members.some(member => member.userId === user.id) ?? false;
  const isLeader = selected?.leaderId === user.id;
  async function select(id: string) {
    await act(async () => {
      const team = await getTeam(id); selectedId.current = id; setSelected(team); setConfirmation(null); setHistory(null);
      if (team.members.some(member => member.userId === user.id)) {
        const items = await teamAudit(id); setHistory({ teamId: id, items }); setMoreHistory(items.length === 50);
      }
    });
  }
  async function confirm() {
    if (!selected || !confirmation) return;
    const team = selected;
    await act(async () => {
      if (confirmation.type === 'leave') {
        const response = await leaveTeam(team.id);
        setNotice(response.deleted ? 'You left the final seat. The team was deleted and its audit history retained.' : 'You left the team. The remaining roster and leadership are up to date.');
        // Membership is committed even if the separate history request fails.
        setConfirmation(null); setRevision(value => value + 1);
        const items = await teamAudit(team.id); setHistory({ teamId: team.id, items }); setMoreHistory(items.length === 50);
      } else {
        await removeTeamMember(team.id, confirmation.userId!); setNotice('Member removed.');
        setConfirmation(null); setRevision(value => value + 1);
      }
    });
  }
  return <section aria-labelledby="dashboard-heading">
    <h1 id="dashboard-heading" className="profile-title">Your hackathon teams</h1>
    <p>Create a team, request a place or redeem an invitation. Leaders manage invitations and requests.</p>
    {error && <div role="alert" className="error-summary" tabIndex={-1} ref={summary}><p>{error}</p><button className="secondary" type="button" onClick={() => { setError(''); if (eventId) setRevision(value => value + 1); else window.location.reload(); }}>Refresh dashboard</button></div>}
    <p role="status" aria-live="polite">{notice}</p>
    <p role="status">{liveStatus}</p>
    <div className="dashboard-toolbar"><div className="field"><label htmlFor="hackathon">Hackathon</label>
      <select id="hackathon" value={eventId} disabled={busy || loading || !events.length} onChange={event => { selectedId.current = null; setEventId(event.target.value); setHistory(null); setNotice(''); setSelected(null); }}>
        {events.map(item => <option key={item.id} value={item.id}>{item.name}{item.active ? '' : ' (inactive)'}</option>)}
      </select></div><button className="secondary" type="button" disabled={busy || loading || !eventId} onClick={() => setRevision(value => value + 1)}>Refresh</button></div>
    {loading ? <p role="status">Loading teams…</p> : !event ? <p>No hackathons are configured. Ask an organizer to configure an event.</p> : <>
      <p>Team limits: {event.minSize}–{event.maxSize}. Smaller teams are marked incomplete.</p>
      <form onSubmit={e => { e.preventDefault(); setFilters(current => ({ ...current, skill: skillDraft.trim().toLowerCase() || undefined })); }}><fieldset disabled={busy}><legend>Filter the team board</legend>
        <label htmlFor="board-skill">Desired skill</label><input id="board-skill" maxLength={40} value={skillDraft} onChange={e => setSkillDraft(e.target.value)} />
        <label htmlFor="board-status">Readiness</label><select id="board-status" value={filters.status ?? ''} onChange={e => setFilters(current => ({ ...current, status: e.target.value || undefined }))}><option value="">All teams</option><option value="READY">Ready</option><option value="INCOMPLETE">Incomplete</option></select>
        <label htmlFor="board-seats">Capacity</label><select id="board-seats" value={filters.openSeats ? 'open' : 'all'} onChange={e => setFilters(current => ({ ...current, openSeats: e.target.value === 'open' }))}><option value="all">All capacities</option><option value="open">Open seats only</option></select>
        <label htmlFor="board-sort">Sort</label><select id="board-sort" value={filters.sort} onChange={e => setFilters(current => ({ ...current, sort: e.target.value as 'id' | 'name' }))}><option value="id">Stable team order</option><option value="name">Name (A–Z)</option></select><button type="submit">Apply skill filter</button>
      </fieldset></form>
      <div className="team-workspace"><div className="team-main">
        {!own && <TeamForm busy={busy} submit={input => { void act(async () => {
          const fingerprint = JSON.stringify({ eventId, input });
          if (pendingCreate.current?.fingerprint !== fingerprint) pendingCreate.current = { fingerprint, key: crypto.randomUUID() };
          await createTeam(eventId, input, pendingCreate.current.key);
          pendingCreate.current = null;
          setNotice('Team created. You are its first member and leader.'); setRevision(value => value + 1);
        }); }} />}
        {selected && <section aria-labelledby="selected-team-heading" className="team-details">
          <h2 id="selected-team-heading">{selected.name}</h2><p>{selected.description || 'No description yet.'}</p>
          <p><strong>{selected.status === 'READY' ? 'Ready' : 'Incomplete'}</strong> · {selected.members.length}/{selected.maxSize} members{selected.status === 'INCOMPLETE' ? ' · needs ' + (selected.minSize - selected.members.length) + ' more' : ''}</p>
          <p>Desired skills: {selected.desiredSkills.join(', ') || 'Not specified'}</p>
          <h3>Members</h3><ul className="member-list">{selected.members.map(member => <li key={member.userId}>
            <span>{member.displayName}{member.userId === user.id ? ' (you)' : ''}{member.leader ? ' · Leader' : ''}</span>
            {isLeader && member.userId !== user.id && <button className="secondary" type="button" disabled={busy} onClick={() => setConfirmation({ type: 'remove', userId: member.userId })}>Remove {member.displayName}</button>}
          </li>)}</ul>
          {isMember && <button className="secondary" type="button" disabled={busy} onClick={() => setConfirmation({ type: 'leave' })}>Leave team</button>}
          {confirmation && <div className="confirmation" role="group" aria-label="Confirm roster change">
            <p>{confirmation.type === 'remove' ? 'Remove this participant from your team?' : selected.members.length === 1 ? 'Leaving deletes the active team. Audit history will be preserved.' : isLeader ? 'Leadership will pass to ' + selected.members.find(member => member.userId !== user.id)?.displayName + ', the earliest remaining member.' : 'Leave this team? Your seat will become available.'}</p>
            <button type="button" disabled={busy} onClick={() => { void confirm(); }}>{busy ? 'Updating…' : confirmation.type === 'leave' ? 'Confirm leave' : 'Confirm removal'}</button>
            <button className="secondary" type="button" disabled={busy} onClick={() => setConfirmation(null)}>Cancel</button>
          </div>}
          {isLeader && <TeamForm key={selected.id} initial={selected} busy={busy} submit={input => { void act(async () => { await updateTeam(selected.id, input); setNotice('Team details saved.'); setRevision(value => value + 1); }); }} />}
          {isLeader && <Suggestions team={selected.id} revision={revision} expired={onSessionExpired} />}
        </section>}
      </div><section className="team-board" aria-labelledby="board-heading"><h2 id="board-heading">Teams in this hackathon</h2>
        {!board.length && <p>No teams yet.</p>}<ul>{board.map(team => <li key={team.id}><button type="button" className="team-select" disabled={busy} onClick={() => { void select(team.id); }} aria-pressed={selected?.id === team.id}>
          <strong>{team.name}</strong><span>{team.memberCount}/{team.maxSize} members · {team.openSeats} open · {team.status === 'READY' ? 'Ready' : 'Incomplete'} · Leader: {team.leaderDisplayName}</span>
        </button></li>)}</ul>
        {cursor && <button className="secondary" type="button" disabled={busy} onClick={() => { void act(async () => { const page = await listTeams(eventId, cursor, filters); setBoard(current => [...current, ...page.items.filter(item => !current.some(old => old.id === item.id))]); setCursor(page.nextCursor); }); }}>Load more teams</button>}
      </section></div>
      <AdmissionPanel key={eventId + ':' + (selected?.id ?? '')} event={eventId} selected={selected} userId={user.id} teamed={!!own} liveRevision={revision} refresh={() => setRevision(value => value + 1)} expired={onSessionExpired} />
    </>}
    {history && <section className="team-history" aria-labelledby="history-heading"><h2 id="history-heading">Team audit history</h2><p className="note">Only current and former participants can read this history. Identifiers remain after team deletion.</p>
      <ol>{history.items.map(item => <li key={item.id}><time dateTime={item.timestamp}>{new Date(item.timestamp).toLocaleString()}</time> · {item.action.toLowerCase().replaceAll('_', ' ')} · actor {item.actorId === user.id ? 'you' : item.actorId.slice(0, 8)} · target {item.targetType === 'TEAM' ? 'team' : item.targetId.slice(0, 8)}</li>)}</ol>
      {moreHistory && <button className="secondary" type="button" disabled={busy} onClick={() => { void act(async () => { const page = await teamAudit(history.teamId, history.items.length); setHistory(previous => previous ? { ...previous, items: [...previous.items, ...page.filter(item => !previous.items.some(old => old.id === item.id))] } : previous); setMoreHistory(page.length === 50); }); }}>Load older changes</button>}
    </section>}
  </section>;
}
