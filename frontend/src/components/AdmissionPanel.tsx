import { useEffect, useRef, useState } from 'react';
import { ApiError, decideRequest, myRequests, redeemInvitation, requestInbox, requestTeam, rotateInvitation, withdrawRequest,
  type JoinRequest, type Team } from '../api/client';

export default function AdmissionPanel({ event, selected, userId, teamed, refresh, expired }: {
  event: string; selected: Team | null; userId: string; teamed: boolean; refresh: () => void; expired: () => void;
}) {
  const [requests, setRequests] = useState<JoinRequest[]>([]);
  const [inbox, setInbox] = useState<JoinRequest[]>([]);
  const [mineCursor, setMineCursor] = useState<string | null>(null);
  const [inboxCursor, setInboxCursor] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [issued, setIssued] = useState<{ code: string; expiresAt: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [revision, setRevision] = useState(0);
  const keys = useRef(new Map<string, string>());
  const errorFocus = useRef<HTMLParagraphElement>(null);
  const leader = selected?.leaderId === userId;
  useEffect(() => { setIssued(null); }, [selected?.id, leader]);
  useEffect(() => { if (error) errorFocus.current?.focus(); }, [error]);
  function failure(reason: unknown) {
    const problem = ApiError.from(reason);
    if (problem.status === 401) expired(); else setError(problem.message + (problem.status === 409 ? ' Refresh to check current capacity and request status, then retry the same action.' : ''));
  }
  useEffect(() => {
    let active = true; setLoading(true);
    Promise.all([myRequests(event), leader && selected ? requestInbox(selected.id) : Promise.resolve({ items: [], nextCursor: null })])
      .then(([mine, received]) => { if (active) { setRequests(mine.items); setMineCursor(mine.nextCursor); setInbox(received.items); setInboxCursor(received.nextCursor); } })
      .catch(reason => { if (active) failure(reason); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
    // Parent remounts when the authenticated identity/event changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [event, selected?.id, leader, revision]);
  async function act(work: () => Promise<void>, reload = true) {
    setBusy(true); setError(''); setNotice('');
    try { await work(); if (reload) setRevision(value => value + 1); } catch (reason) { failure(reason); } finally { setBusy(false); }
  }
  function key(intent: string) { let value = keys.current.get(intent); if (!value) { value = crypto.randomUUID(); keys.current.set(intent, value); } return value; }
  const pending = requests.find(row => row.teamId === selected?.id && row.state === 'PENDING');
  return <section aria-labelledby="admission-heading" aria-busy={busy || loading}>
    <h2 id="admission-heading">Join a team</h2>
    {error && <p role="alert" className="error-summary" ref={errorFocus} tabIndex={-1}>{error}</p>}
    <p role="status">{notice}</p>
    <button type="button" className="secondary" disabled={busy || loading} onClick={() => setRevision(value => value + 1)}>Refresh requests</button>
    {!teamed && <form onSubmit={e => { e.preventDefault(); void act(async () => {
      const intent = 'invite:' + code.trim(); await redeemInvitation(event, code.trim(), key(intent)); keys.current.delete(intent); setCode(''); setNotice('Admission confirmed. Dashboard refreshed.'); refresh();
    }); }}><fieldset disabled={busy || loading}><legend>Have an invitation?</legend>
      <label htmlFor="invite-code">Invitation code</label><input id="invite-code" required maxLength={43} value={code} autoComplete="off" spellCheck={false} onChange={e => setCode(e.target.value)} aria-describedby="invite-help" />
      <p id="invite-help" className="note">Codes expire after 24 hours. A valid code joins directly if a seat is available.</p><button type="submit">Redeem invitation</button>
    </fieldset></form>}
    {!teamed && selected && <p>{pending ? 'Your request for this team is pending.' : <button disabled={busy || loading} type="button" onClick={() => { void act(async () => { await requestTeam(selected.id); setNotice('Request sent to the current leader.'); }); }}>Request to join {selected.name}</button>}</p>}
    {loading ? <p role="status">Loading requests…</p> : <><h3>Your requests</h3>{!requests.length && <p>No requests yet.</p>}
      <ul>{requests.map(row => <li key={row.id}>Team {row.teamId === selected?.id ? selected.name : row.teamId.slice(0, 8)} · {row.state.toLowerCase()}
        {row.state === 'PENDING' && <button type="button" className="secondary" disabled={busy} onClick={() => { void act(async () => { await withdrawRequest(row); setNotice('Request withdrawn.'); }); }}>Withdraw request</button>}
      </li>)}</ul>{mineCursor && <button type="button" disabled={busy} onClick={() => { void act(async () => { const page = await myRequests(event, mineCursor); setRequests(rows => [...rows, ...page.items]); setMineCursor(page.nextCursor); }, false); }}>Load more of your requests</button>}</>}
    {leader && selected && <><h3>Team invitations</h3><p>Generating a code immediately invalidates the previous code. Share it privately.</p>
      <button type="button" disabled={busy || loading} onClick={() => { void act(async () => { setIssued(null); setIssued(await rotateInvitation(selected.id)); }); }}>Generate / rotate invitation</button>
      {issued && <div role="status"><label htmlFor="issued-code">Copy this code now; it will not be shown again</label><input id="issued-code" readOnly value={issued.code} onFocus={e => e.target.select()} /><p>Expires {new Date(issued.expiresAt).toLocaleString()}</p></div>}
      <h3>Leader request inbox</h3>{!inbox.some(row => row.state === 'PENDING') && <p>No pending requests.</p>}
      <ul>{inbox.filter(row => row.state === 'PENDING').map(row => <li key={row.id}>{row.displayName}
        <button type="button" disabled={busy || loading} onClick={() => { void act(async () => { const intent = 'accept:' + row.id; await decideRequest(row, true, key(intent)); keys.current.delete(intent); setNotice('Acceptance confirmed. Dashboard refreshed.'); refresh(); }); }}>Accept {row.displayName}</button>
        <button type="button" className="secondary" disabled={busy || loading} onClick={() => { void act(async () => { await decideRequest(row, false); setNotice('Request declined.'); }); }}>Decline {row.displayName}</button>
      </li>)}</ul>{inboxCursor && <button type="button" disabled={busy} onClick={() => { void act(async () => { const page = await requestInbox(selected.id, inboxCursor); setInbox(rows => [...rows, ...page.items]); setInboxCursor(page.nextCursor); }, false); }}>Load more pending requests</button>}</>}
  </section>;
}
