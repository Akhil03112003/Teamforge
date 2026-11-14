import { useEffect, useRef, useState } from 'react';
import { ApiError, currentUser, getHealth, logout, type Participant } from './api/client';
import AuthForm from './components/AuthForm';
import ProfileForm from './components/ProfileForm';
import TeamDashboard from './components/TeamDashboard';

type View = 'discover' | 'my-team' | 'requests' | 'profile';
const navigation: { id: View; label: string; hint: string }[] = [
  { id: 'discover', label: 'Discover teams', hint: 'Find your people' },
  { id: 'my-team', label: 'My team', hint: 'Build together' },
  { id: 'requests', label: 'Invitations & requests', hint: 'Manage your next step' },
  { id: 'profile', label: 'Your profile', hint: 'Skills & availability' },
];
function route(): View { const value = window.location.hash.slice(1); return navigation.some(item => item.id === value) ? value as View : 'discover'; }

export default function App() {
  const [user, setUser] = useState<Participant | null>(null);
  const [restoring, setRestoring] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [error, setError] = useState('');
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [view, setView] = useState<View>(route);
  const [connection, setConnection] = useState('Checking connection…');
  const main = useRef<HTMLElement>(null);
  const signOutLock = useRef(false);
  useEffect(() => { document.title = user ? (navigation.find(item => item.id === view)?.label ?? 'Workspace') + ' | TeamForge' : 'TeamForge · Find your team'; }, [view, user]);
  useEffect(() => { const changed = () => { setView(route()); requestAnimationFrame(() => main.current?.focus()); }; window.addEventListener('hashchange', changed); return () => window.removeEventListener('hashchange', changed); }, []);
  useEffect(() => {
    const controller = new AbortController(); let active = true;
    setRestoring(true); setError('');
    currentUser(controller.signal).then(value => { if (active) setUser(value); }).catch(reason => {
      if (!active) return;
      if (ApiError.from(reason).status === 401) setUser(null); else setError(ApiError.from(reason).message);
    }).finally(() => { if (active) setRestoring(false); });
    getHealth(controller.signal).then(() => { if (active) setConnection('Service available'); }).catch(() => { if (active) setConnection('Connection unavailable. Check your network and retry.'); });
    return () => { active = false; controller.abort(); };
  }, [attempt]);
  function expired() { setUser(null); setNotice('Your session expired. Sign in to continue.'); }
  async function signOut() {
    if (signOutLock.current) return; signOutLock.current = true; setBusy(true); setError('');
    try { await logout(); setUser(null); setNotice('You are signed out.'); }
    catch (reason) { if (ApiError.from(reason).status === 401) expired(); else setError(ApiError.from(reason).message); }
    finally { signOutLock.current = false; setBusy(false); }
  }
  return <>
    <a className="skip-link" href="#main" onClick={event => { event.preventDefault(); main.current?.focus(); main.current?.scrollIntoView({ block: 'start' }); }}>Skip to main content</a>
    <header className={user ? 'app-header signed-in' : 'app-header'}><a className="wordmark" href="#discover" aria-label="TeamForge home"><span className="brand-mark" aria-hidden="true">T</span>TeamForge</a>
      <span className="header-purpose">Make room for a great team.</span>{user && <><span className="account-name">{user.profile.displayName}</span><button className="secondary signout" type="button" disabled={busy} onClick={() => { void signOut(); }}>{busy ? 'Signing out…' : 'Sign out'}</button></>}
    </header>
    <div className={user ? 'app-shell' : 'welcome-shell'}>
      {user && <aside className="app-sidebar"><nav aria-label="Main navigation">{navigation.map(item => <a key={item.id} href={'#' + item.id} aria-current={view === item.id ? 'page' : undefined}><strong>{item.label}</strong><span>{item.hint}</span></a>)}</nav>
      </aside>}
      <main id="main" ref={main} tabIndex={-1} className={user ? 'workspace' : 'welcome-main'}>
        {restoring ? <div role="status" className="loading-state"><span className="skeleton" />Restoring your session…</div> : !user && error ? <section className="empty-state"><h1>Let’s reconnect</h1><p role="alert">{error}</p><button type="button" onClick={() => setAttempt(value => value + 1)}>Retry connection</button></section> : user ? <>
          {error && <div role="alert" className="error-summary"><p>{error}</p><button type="button" className="secondary" onClick={() => setError('')}>Dismiss message</button></div>}
          <div hidden={view === 'profile'}><TeamDashboard key={user.id} view={view === 'profile' ? 'discover' : view} user={user} onSessionExpired={expired} /></div>
          <section hidden={view !== 'profile'} aria-labelledby="profile-heading"><div className="page-heading"><h1 id="profile-heading">Your profile</h1><p>Show what you bring, and make collaboration easier.</p></div><ProfileForm key={user.id} profile={user.profile} onSaved={profile => setUser(previous => previous ? { ...previous, profile } : previous)} onSessionExpired={expired} /></section>
        </> : <><section className="welcome-copy" aria-labelledby="welcome"><h1 id="welcome">Find the people.<br /><em>Build the thing.</em></h1><p className="intro">Your next hackathon starts with the right teammates. Bring your skills. Find the missing pieces.</p><ol className="journey"><li><strong>Make your introduction</strong><span>Add your skills and collaboration hours.</span></li><li><strong>Find a team that fits</strong><span>Browse open seats or start something new.</span></li><li><strong>Get ready to build</strong><span>Join by invitation or request a place.</span></li></ol></section>
          <AuthForm key={mode} mode={mode} notice={notice} onModeChange={next => { setMode(next); setNotice(''); }} onAuthenticated={value => { setUser(value); setNotice(''); }} onRegistered={() => { setMode('login'); setNotice('Account created. Sign in to continue.'); }} />
        </>}
      </main>
    </div>
    <footer><span>TeamForge · Built for people who build together.</span><details><summary>Connection status</summary><p role="status">{connection}</p><button type="button" className="secondary" onClick={() => setAttempt(value => value + 1)}>Check again</button></details></footer>
  </>;
}
