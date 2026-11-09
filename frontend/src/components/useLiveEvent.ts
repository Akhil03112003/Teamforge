import { useEffect, useRef, useState } from 'react';
import { ApiError, checkSession } from '../api/client';

export function useLiveEvent(event: string, refresh: () => void, expired: () => void) {
  const [status, setStatus] = useState('Connecting to live updates…');
  const latest = useRef({ refresh, expired }); latest.current = { refresh, expired };
  useEffect(() => {
    if (!event) return;
    let active = true; let scheduled: ReturnType<typeof setTimeout> | undefined;
    const invalidate = () => { if (!scheduled) scheduled = setTimeout(() => { scheduled = undefined; if (active) latest.current.refresh(); }, 250); };
    const source = new EventSource('/api/v1/hackathons/' + encodeURIComponent(event) + '/events');
    source.addEventListener('ready', () => { if (active) { setStatus('Live updates connected'); invalidate(); } });
    source.addEventListener('invalidate', invalidate);
    source.addEventListener('session-expired', () => { source.close(); if (active) latest.current.expired(); });
    source.onerror = () => {
      if (!active) return; setStatus('Live connection interrupted. Reconnecting; periodic refresh continues.');
      void checkSession().catch(reason => { if (active && ApiError.from(reason).status === 401) { source.close(); latest.current.expired(); } });
    };
    // Also reconciles missed events, process restarts and notifications from another replica.
    const interval = setInterval(() => { invalidate(); void checkSession().catch(reason => { if (active && ApiError.from(reason).status === 401) { source.close(); latest.current.expired(); } }); }, 30_000);
    return () => { active = false; source.close(); clearInterval(interval); if (scheduled) clearTimeout(scheduled); };
  }, [event]);
  return status;
}
