import { useEffect, useState } from 'react';
import { getHealth } from './api/client';

type Connection = 'checking' | 'connected' | 'unavailable';

export default function App() {
  const [connection, setConnection] = useState<Connection>('checking');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 5000);
    let active = true;
    setConnection('checking');
    getHealth(controller.signal)
      .then(() => { if (active) setConnection('connected'); })
      .catch(() => { if (active) setConnection('unavailable'); })
      .finally(() => window.clearTimeout(timeout));
    return () => { active = false; window.clearTimeout(timeout); controller.abort(); };
  }, [attempt]);

  const status = {
    checking: 'Checking backend connection…',
    connected: 'Backend connected',
    unavailable: 'Backend unavailable. Start the backend, then check again.',
  }[connection];

  return (
    <>
      <a className="skip-link" href="#main">Skip to content</a>
      <header><a className="wordmark" href="/">TeamForge<span aria-hidden="true"> /</span></a>
        <span className="phase">Application foundation</span></header>
      <main id="main" tabIndex={-1}>
        <section aria-labelledby="welcome">
          <h1 id="welcome">Good teams<br />start with<br /><em>connection.</em></h1>
          <p className="intro">A place to find the people who bring something different to your hackathon team.</p>
          <p className="scope">The foundation is taking shape. Team discovery and sign-in are coming in later milestones.</p>
        </section>
        <section className="connection" aria-labelledby="connection-heading">
          <h2 id="connection-heading">Connection check</h2>
          <p role="status" aria-live="polite" aria-atomic="true">
            <span className={'indicator ' + connection} aria-hidden="true" />{status}
          </p>
          <button type="button" disabled={connection === 'checking'}
            onClick={() => setAttempt(value => value + 1)}>
            {connection === 'checking' ? 'Checking…' : 'Check again'}
          </button>
          <p className="note">This checks whether the backend is responding. It does not check database readiness.</p>
        </section>
      </main>
      <footer>TeamForge <span>Built for people who build together.</span></footer>
    </>
  );
}
