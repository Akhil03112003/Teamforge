import { useEffect, useRef, useState, type FormEvent } from 'react';
import ConfirmDialog from './ConfirmDialog';
import AdmissionPanel from './AdmissionPanel';
import Suggestions from './Suggestions';
import { useLiveEvent } from './useLiveEvent';
import { useMounted } from './useMounted';
import { ApiError, createTeam, getTeam, leaveTeam, listHackathons, listTeams, myTeam, removeTeamMember, teamAudit, updateTeam,
  type AuditEvent, type Hackathon, type Participant, type Team, type TeamInput, type TeamSummary } from '../api/client';

function TeamForm({ initial, busy, error, submit }: { initial?: Team; busy: boolean; error?: string; submit: (input: TeamInput) => void }) {
  const [name, setName] = useState(initial?.name ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [skills, setSkills] = useState(initial?.desiredSkills.join(', ') ?? '');
  const prefix = initial ? 'edit-team-' : 'create-team-';
  function save(event: FormEvent) { event.preventDefault(); submit({ name: name.trim(), description,
    desiredSkills: skills.split(',').map(skill => skill.trim()).filter(Boolean) }); }
  return <form onSubmit={save} aria-busy={busy} aria-describedby={error ? 'dashboard-error' : undefined}><fieldset disabled={busy}>
    <legend>{initial ? 'Edit team details' : 'Start a team'}</legend>
    <div className="field"><label htmlFor={prefix + 'name'}>Team name</label><input id={prefix + 'name'} aria-describedby={error ? 'dashboard-error' : undefined} required maxLength={120} value={name} onChange={event => setName(event.target.value)} /></div>
    <div className="field"><label htmlFor={prefix + 'description'}>Description</label><textarea id={prefix + 'description'} aria-describedby={error ? 'dashboard-error' : undefined} maxLength={2000} rows={3} value={description} onChange={event => setDescription(event.target.value)} /></div>
    <div className="field"><label htmlFor={prefix + 'skills'}>Desired skills</label><input id={prefix + 'skills'} value={skills} maxLength={820} onChange={event => setSkills(event.target.value)} aria-describedby={prefix + 'hint' + (error ? ' dashboard-error' : '')} />
      <p id={prefix + 'hint'} className="note">Up to 20 comma-separated skill tags. Each tag can have up to 40 characters.</p></div>
    <button type="submit">{busy ? 'Saving…' : initial ? 'Save team details' : 'Create team'}</button>
  </fieldset></form>;
}

export default function TeamDashboard({ user, view, onSessionExpired }: { user: Participant; view: 'discover' | 'my-team' | 'requests'; onSessionExpired: () => void }) {
  const [events, setEvents] = useState<Hackathon[]>([]);
  const [eventId, setEventId] = useState('');
  const [board, setBoard] = useState<TeamSummary[]>([]);
  const [search, setSearch] = useState('');
  const actionLock = useRef(false);
  const mounted = useMounted();
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
  const [filters, setFilters] = useState<{ q?: string; skill?: string; openSeats?: boolean; status?: string; sort?: 'id' | 'name' }>({ sort: 'id' });
  const selectedId = useRef<string | null>(null);
  const teamNames = useRef<Record<string, string>>({});
  const loadedEvent = useRef('');
  const liveStatus = useLiveEvent(eventId, () => setRevision(value => value + 1), onSessionExpired);
  const [confirmation, setConfirmation] = useState<{ type: 'leave' | 'remove'; userId?: string } | null>(null);
  const summary = useRef<HTMLDivElement>(null);
  const pendingCreate = useRef<{ fingerprint: string; key: string } | null>(null);
  useEffect(() => { if (error) summary.current?.focus(); }, [error]);
  useEffect(() => {
    const restoreEvent = () => {
      const requested = new URLSearchParams(window.location.search).get('hackathon');
      const next = events.find(item => item.id === requested)?.id ?? events[0]?.id;
      if (next && next !== eventId) { selectedId.current = null; setEventId(next); setSelected(null); setHistory(null); setNotice(''); }
    };
    window.addEventListener('popstate', restoreEvent);
    return () => window.removeEventListener('popstate', restoreEvent);
  }, [events, eventId]);
  function failure(reason: unknown) {
    if (!mounted.current) return;
    const problem = ApiError.from(reason);
    if (problem.status === 401) onSessionExpired(); else setError(problem.message + ' ' + Object.entries(problem.fieldErrors).map(([field, message]) => field + ': ' + message).join(' '));
  }
  useEffect(() => {
    let active = true;
    listHackathons().then(items => { if (active) { const requested = new URLSearchParams(window.location.search).get('hackathon'); setEvents(items); setEventId(items.find(item => item.id === requested)?.id ?? items[0]?.id ?? ''); if (!items.length) setLoading(false); } })
      .catch(reason => { if (active) { failure(reason); setLoading(false); } });
    return () => { active = false; };
    // The dashboard is remounted when the authenticated participant changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  useEffect(() => {
    if (!eventId) return;
    let active = true; if (loadedEvent.current !== eventId) setLoading(true); setError('');
    Promise.all([myTeam(eventId), listTeams(eventId, undefined, filters)]).then(async ([team, result]) => {
      if (!active) return;
      let viewed = team;
      let selection = selectedId.current;
      if (selection && selection !== team?.id) {
        try { viewed = await getTeam(selection); } catch (reason) { if (ApiError.from(reason).status !== 404) throw reason; if (selectedId.current === selection) { selectedId.current = null; selection = null; } }
      }
      if (!active) return;
      for (const item of result.items) teamNames.current[item.id] = item.name;
      if (team) teamNames.current[team.id] = team.name;
      setOwn(team); if (selectedId.current === selection) setSelected(viewed); setBoard(result.items); setCursor(result.nextCursor);
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
    if (actionLock.current) return; actionLock.current = true;
    setBusy(true); setError('');
    try { await operation(); } catch (reason) { failure(reason); } finally { actionLock.current = false; setBusy(false); }
  }
  useEffect(() => { if (view === 'my-team') { selectedId.current = own?.id ?? null; setSelected(own); } }, [view, own]);
  const visibleBoard = board;
  const event = events.find(item => item.id === eventId);
  const isMember = selected?.members.some(member => member.userId === user.id) ?? false;
  const isLeader = selected?.leaderId === user.id;
  async function select(id: string) {
    selectedId.current = id;
    await act(async () => {
      const team = await getTeam(id); if (selectedId.current !== id || !mounted.current) return;
      teamNames.current[id] = team.name; setSelected(team); setConfirmation(null); setHistory(null);
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
    <div className="page-heading"><h1 id="dashboard-heading">{view === 'discover' ? 'Find your team' : view === 'my-team' ? 'My team' : 'Invitations & requests'}</h1>
    <p>{view === 'discover' ? 'Explore the people and projects looking for your skills.' : view === 'my-team' ? 'A shared space to get your team ready to build.' : 'Keep track of your next move. Leaders manage their team’s inbox here.'}</p></div>
    {error && <div id="dashboard-error" role="alert" className="error-summary" tabIndex={-1} ref={summary}><p>{error}</p><button className="secondary" type="button" onClick={() => { setError(''); if (eventId) setRevision(value => value + 1); else window.location.reload(); }}>Refresh dashboard</button></div>}
    <p className={notice ? "notice" : "sr-only"} role="status" aria-live="polite">{notice}</p>
    <p className="live-status" role="status"><span className={'indicator ' + (liveStatus === 'Live updates connected' ? 'connected' : 'waiting')} aria-hidden="true" />{liveStatus}</p>
    <div className="dashboard-toolbar"><div className="field"><label htmlFor="hackathon">Hackathon</label>
      <select id="hackathon" value={eventId} disabled={busy || loading || !events.length} onChange={event => { const url = new URL(window.location.href); url.searchParams.set('hackathon', event.target.value); window.history.replaceState(null, '', url); selectedId.current = null; setEventId(event.target.value); setHistory(null); setNotice(''); setSelected(null); }}>
        {events.map(item => <option key={item.id} value={item.id}>{item.name}{item.active ? '' : ' (inactive)'}</option>)}
      </select></div><button className="secondary" type="button" disabled={busy || loading || !eventId} onClick={() => setRevision(value => value + 1)}>Refresh</button></div>
    {loading ? <div role="status" className="loading-state"><span className="skeleton" />Loading your workspace…</div> : !event ? <p>No hackathons are configured. Ask an organizer to configure an event.</p> : <>
      <p>Team limits: {event.minSize}–{event.maxSize}. Smaller teams are marked incomplete.</p>
      <form className="board-filters" hidden={view !== 'discover'} onSubmit={e => { e.preventDefault(); setFilters(current => ({ ...current, q: search.trim() || undefined, skill: skillDraft.trim().toLowerCase() || undefined })); }}><fieldset disabled={busy}><legend>Refine your search</legend><div className="filter-grid"><div className="field"><label htmlFor="team-search">Search teams</label><div className="search-row"><input id="team-search" type="search" maxLength={120} value={search} onChange={e => setSearch(e.target.value)} aria-describedby="search-hint" /><button type="submit">Search</button><button type="button" className="secondary" onClick={() => { setSearch(''); setSkillDraft(''); setFilters({ sort: 'id' }); }}>Clear filters</button></div><p className="note" id="search-hint">Search team names and descriptions across this hackathon.</p></div><details className="advanced-filters"><summary>More filters & sorting</summary><div className="filter-controls">
        <div className="field"><label htmlFor="board-skill">Desired skill</label><input id="board-skill" maxLength={40} value={skillDraft} onChange={e => setSkillDraft(e.target.value)} /></div>
        <div className="field"><label htmlFor="board-status">Readiness</label><select id="board-status" value={filters.status ?? ''} onChange={e => setFilters(current => ({ ...current, status: e.target.value || undefined }))}><option value="">All teams</option><option value="READY">Ready</option><option value="INCOMPLETE">Incomplete</option></select></div>
        <div className="field"><label htmlFor="board-seats">Capacity</label><select id="board-seats" value={filters.openSeats ? 'open' : 'all'} onChange={e => setFilters(current => ({ ...current, openSeats: e.target.value === 'open' }))}><option value="all">All capacities</option><option value="open">Open seats only</option></select></div>
        <div className="field"><label htmlFor="board-sort">Sort</label><select id="board-sort" value={filters.sort} onChange={e => setFilters(current => ({ ...current, sort: e.target.value as 'id' | 'name' }))}><option value="id">Stable team order</option><option value="name">Name (A–Z)</option></select></div><button type="submit">Apply filters</button></div></details></div>
      </fieldset></form>
      <div className={"team-workspace " + (view === "my-team" ? "single-workspace" : "")} hidden={view === "requests"}><div className="team-main">
        {!own && view === 'my-team' && <div className="panel"><h2>Start something together</h2><p>You are not on a team in this hackathon. Create one and invite your first teammates.</p><TeamForm busy={busy} error={error} submit={input => { void act(async () => {
          const fingerprint = JSON.stringify({ eventId, input });
          if (pendingCreate.current?.fingerprint !== fingerprint) pendingCreate.current = { fingerprint, key: crypto.randomUUID() };
          await createTeam(eventId, input, pendingCreate.current.key);
          pendingCreate.current = null;
          setNotice('Team created. You are its first member and leader.'); setRevision(value => value + 1);
        }); }} /></div>}
        {!selected && view === 'discover' && <div className="empty-state"><h2>A good fit starts here</h2><p>Choose a team to see its project, people and open seats.</p><a className="button-link" href="#my-team">Start a team instead</a></div>}
        {selected && <section aria-labelledby="selected-team-heading" className="team-details">
          <h2 id="selected-team-heading">{selected.name}</h2><p>{selected.description || 'No description yet.'}</p>
          <p><strong className={"status-badge " + selected.status.toLowerCase()}>{selected.status === 'READY' ? 'Ready' : 'Incomplete'}</strong> · {selected.members.length}/{selected.maxSize} members{selected.status === 'INCOMPLETE' ? ' · needs ' + (selected.minSize - selected.members.length) + ' more' : ''}</p>
          <p>Desired skills: {selected.desiredSkills.join(', ') || 'Not specified'}</p>
          <h3>Members</h3><ul className="member-list">{selected.members.map(member => <li key={member.userId}>
            <span>{member.displayName}{member.userId === user.id ? ' (you)' : ''}{member.leader ? ' · Leader' : ''}</span>
            {isLeader && member.userId !== user.id && <button className="secondary" type="button" disabled={busy} onClick={() => setConfirmation({ type: 'remove', userId: member.userId })}>Remove {member.displayName}</button>}
          </li>)}</ul>
          {isMember && <button className="secondary" type="button" disabled={busy} onClick={() => setConfirmation({ type: 'leave' })}>Leave team</button>}
          {confirmation && <ConfirmDialog title={confirmation.type === 'leave' ? 'Leave this team?' : 'Remove this member?'} busy={busy} confirmLabel={confirmation.type === 'leave' ? 'Confirm leave' : 'Confirm removal'} confirm={() => { void confirm(); }} cancel={() => setConfirmation(null)}>
            <p>{confirmation.type === 'remove' ? 'This participant will lose their place on your team.' : selected.members.length === 1 ? 'You are the last member. Leaving deletes the active team; its audit history stays available.' : isLeader ? 'Leadership will pass to ' + selected.members.find(member => member.userId !== user.id)?.displayName + ', the earliest remaining member.' : 'Your seat will become available to another participant.'}</p>
          </ConfirmDialog>}
          {isMember && <a className="button-link secondary" href="#requests">Manage invitations & requests</a>}
          {isLeader && <details className="team-edit"><summary>Edit team details</summary><TeamForm key={selected.id} initial={selected} busy={busy} error={error} submit={input => { void act(async () => { await updateTeam(selected.id, input); setNotice('Team details saved.'); setRevision(value => value + 1); }); }} /></details>}
          {isLeader && <Suggestions team={selected.id} revision={revision} expired={onSessionExpired} />}
        </section>}
      </div><section hidden={view !== 'discover'} className="team-board" aria-labelledby="board-heading"><h2 id="board-heading">Teams in this hackathon</h2>
        {!visibleBoard.length && <div className="empty-state"><h3>No matching teams</h3><p>Try a different filter, load another page, or start your own team.</p></div>}<ul>{visibleBoard.map(team => <li key={team.id}><button type="button" className="team-select" disabled={busy} onClick={() => { void select(team.id); }} aria-pressed={selected?.id === team.id}>
          <strong>{team.name}</strong><span className="team-description">{team.description || "A new team, ready for its next idea."}</span><span>{team.memberCount}/{team.maxSize} members · {team.openSeats} open · {team.status === 'READY' ? 'Ready' : 'Incomplete'} · Leader: {team.leaderDisplayName}</span>
        </button></li>)}</ul>
        {cursor && <button className="secondary" type="button" disabled={busy} onClick={() => { void act(async () => { const page = await listTeams(eventId, cursor, filters); setBoard(current => [...current, ...page.items.filter(item => !current.some(old => old.id === item.id))]); setCursor(page.nextCursor); }); }}>Load more teams</button>}
      </section></div>
      <AdmissionPanel teamNames={teamNames.current} inspect={id => { window.location.hash = 'discover'; void select(id); }} compact={view !== 'requests'} key={eventId + ':' + (view === 'requests' ? 'requests' : selected?.id ?? '')} event={eventId} selected={view === 'requests' ? own : selected} userId={user.id} teamed={!!own} liveRevision={revision} refresh={() => setRevision(value => value + 1)} expired={onSessionExpired} />
    </>}
    {history && <section hidden={view !== 'my-team'} className="team-history" aria-labelledby="history-heading"><h2 id="history-heading">Team audit history</h2><p className="note">Only current and former participants can read this history. Identifiers remain after team deletion.</p>
      <ol>{history.items.map(item => <li key={item.id}><time dateTime={item.timestamp}>{new Date(item.timestamp).toLocaleString()}</time> · {item.action.toLowerCase().replaceAll('_', ' ')} · actor {item.actorId === user.id ? 'you' : item.actorId.slice(0, 8)} · target {item.targetType === 'TEAM' ? 'team' : item.targetId.slice(0, 8)}</li>)}</ol>
      {moreHistory && <button className="secondary" type="button" disabled={busy} onClick={() => { void act(async () => { const page = await teamAudit(history.teamId, history.items.length); setHistory(previous => previous ? { ...previous, items: [...previous.items, ...page.filter(item => !previous.items.some(old => old.id === item.id))] } : previous); setMoreHistory(page.length === 50); }); }}>Load older changes</button>}
    </section>}
  </section>;
}
