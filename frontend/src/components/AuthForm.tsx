import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError, login, register, type Participant } from '../api/client';

interface Props {
  mode: 'login' | 'register';
  onModeChange: (mode: 'login' | 'register') => void;
  onAuthenticated: (user: Participant) => void;
  onRegistered: () => void;
  notice: string;
}

export default function AuthForm({ mode, onModeChange, onAuthenticated, onRegistered, notice }: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const summary = useRef<HTMLDivElement>(null);
  useEffect(() => { if (error) summary.current?.focus(); }, [error]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true); setError(null);
    try {
      if (mode === 'register') {
        await register({ email: email.trim(), password, displayName: displayName.trim() });
        setPassword('');
        onRegistered();
      } else {
        onAuthenticated(await login(email.trim(), password));
        setPassword('');
      }
    } catch (reason) { setError(ApiError.from(reason)); }
    finally { setBusy(false); }
  }
  const fieldError = (name: string) => error?.fieldErrors[name];
  return (
    <section className="auth-panel" aria-labelledby="auth-heading">
      <h2 id="auth-heading">{mode === 'login' ? 'Sign in to TeamForge' : 'Create your account'}</h2>
      {notice && <p role="status">{notice}</p>}
      {error && <div className="error-summary" role="alert" tabIndex={-1} ref={summary}>
        <p>{error.message}</p>
        {Object.entries(error.fieldErrors).map(([field, message]) => <p key={field}>{field}: {message}</p>)}
      </div>}
      <form onSubmit={submit} aria-busy={busy}>
        <fieldset disabled={busy}>
          <legend className="sr-only">Account details</legend>
          {mode === 'register' && <div className="field">
            <label htmlFor="displayName">Display name</label>
            <input id="displayName" autoComplete="nickname" required maxLength={80}
              value={displayName} onChange={event => setDisplayName(event.target.value)}
              aria-invalid={!!fieldError('displayName')} />
          </div>}
          <div className="field">
            <label htmlFor="email">Email</label>
            <input id="email" type="email" autoComplete="email" required maxLength={254}
              value={email} onChange={event => setEmail(event.target.value)}
              aria-invalid={!!fieldError('email')} />
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input id="password" type="password" required maxLength={72}
              minLength={mode === 'register' ? 12 : undefined}
              autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
              value={password} onChange={event => setPassword(event.target.value)}
              aria-describedby={mode === 'register' ? 'password-hint' : undefined}
              aria-invalid={!!fieldError('password')} />
            {mode === 'register' && <p id="password-hint" className="note">Use at least 12 characters and no more than 72 UTF-8 bytes. Avoid a password you use elsewhere.</p>}
          </div>
          <button type="submit">{busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}</button>
        </fieldset>
      </form>
      <button className="text-button" type="button" disabled={busy}
        onClick={() => onModeChange(mode === 'login' ? 'register' : 'login')}>
        {mode === 'login' ? 'New here? Create an account' : 'Already registered? Sign in'}
      </button>
    </section>
  );
}
