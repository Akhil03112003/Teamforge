import { useEffect, useState } from 'react';
import { ApiError, currentUser, getHealth, logout, type Participant } from './api/client';
import AuthForm from './components/AuthForm';
import ProfileForm from './components/ProfileForm';

type Connection = 'checking' | 'connected' | 'unavailable';

export default function App() {
  const [connection, setConnection] = useState<Connection>('checking');
  const [attempt, setAttempt] = useState(0);
  const [user, setUser] = useState<Participant | null>(null);
  const [restoring, setRestoring] = useState(true);
  const [sessionAttempt, setSessionAttempt] = useState(0);
  const [sessionError, setSessionError] = useState('');
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [notice, setNotice] = useState('');
  const [signingOut, setSigningOut] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setRestoring(true); setSessionError('');
    currentUser(controller.signal).then(value => { if (active) setUser(value); })
      .catch(reason => {
        if (!active) return;
        if (reason instanceof ApiError && reason.status === 401) setUser(null);
        else setSessionError(ApiError.from(reason).message);
      }).finally(() => { if (active) setRestoring(false); });
    return () => { active = false; controller.abort(); };
  }, [sessionAttempt]);

  async function signOut() {
    setSigningOut(true); setSessionError('');
    try { await logout(); setUser(null); setNotice('You are signed out.'); }
    catch (reason) { setSessionError(ApiError.from(reason).message); }
    finally { setSigningOut(false); }
  }

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
        {user ? <button className="secondary" type="button" disabled={signingOut} onClick={signOut}>
          {signingOut ? 'Signing out…' : 'Sign out'}</button> : <span className="phase">Find your people.</span>}</header>
      <main id="main" tabIndex={-1} className={user ? 'profile-page' : ''}>
        {restoring ? <p role="status">Restoring your session…</p> : sessionError ?
          <section><p role="alert">{sessionError}</p><button type="button" onClick={() => setSessionAttempt(value => value + 1)}>Try again</button></section> :
          user ? <section aria-labelledby="profile-heading">
            <h1 id="profile-heading" className="profile-title">Your participant profile</h1>
            <p>Tell future teammates what you bring and when you can collaborate.</p>
            <ProfileForm profile={user.profile}
              onSaved={profile => setUser(previous => previous ? { ...previous, profile } : previous)}
              onSessionExpired={() => { setUser(null); setNotice('Your session expired. Please sign in again.'); }} />
          </section> : <>
        <section aria-labelledby="welcome">
          <h1 id="welcome">Good teams<br />start with<br /><em>connection.</em></h1>
          <p className="intro">A place to find the people who bring something different to your hackathon team.</p>
          <p className="scope">Create your profile today. Team discovery is coming in a later milestone.</p>
        </section>
        <AuthForm key={mode} mode={mode} notice={notice} onModeChange={next => { setMode(next); setNotice(''); }}
          onAuthenticated={value => { setUser(value); setNotice(''); }}
          onRegistered={() => { setMode('login'); setNotice('Account created. Sign in to continue.'); }} />
        </>}
      </main>
      <section className="connection connection-footer" aria-labelledby="connection-heading">
          <h2 id="connection-heading">Backend connection</h2>
          <p role="status" aria-live="polite" aria-atomic="true">
            <span className={'indicator ' + connection} aria-hidden="true" />{status}
          </p>
          <button type="button" disabled={connection === 'checking'}
            onClick={() => setAttempt(value => value + 1)}>
            {connection === 'checking' ? 'Checking…' : 'Check again'}
          </button>
          <p className="note">This checks whether the backend is responding. It does not check database readiness.</p>
      </section>
      <footer>TeamForge <span>Built for people who build together.</span></footer>
    </>
  );
}
