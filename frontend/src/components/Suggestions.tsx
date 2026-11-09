import { useEffect, useState } from 'react';
import { ApiError, teammateSuggestions, type Suggestion } from '../api/client';

export default function Suggestions({ team, revision, expired }: { team: string; revision: number; expired: () => void }) {
  const [items, setItems] = useState<Suggestion[]>([]); const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  useEffect(() => {
    let active = true; setLoading(true); setError('');
    teammateSuggestions(team).then(rows => { if (active) setItems(rows); }).catch(reason => { if (active) { const problem = ApiError.from(reason); if (problem.status === 401) expired(); else setError(problem.message); } }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [team, revision]);
  return <section aria-labelledby="suggestions-heading"><h3 id="suggestions-heading">Suggested teammates</h3><p className="note">70% uncovered desired skills, 30% shared collaboration time. Availability defaults are explained below each score; private schedules are not shown.</p>
    {loading ? <p role="status">Ranking eligible participants…</p> : error ? <p role="alert">{error}</p> : !items.length ? <p>No eligible suggestions, or this team is full.</p> : <ol>{items.map(row => <li key={row.userId}><strong>{row.displayName}: {row.score}/100</strong><p>Skills {row.skillScore}/100 · Collaboration {row.timeScore}/100</p><ul>{row.reasons.map(reason => <li key={reason}>{reason}</li>)}</ul></li>)}</ol>}
  </section>;
}
