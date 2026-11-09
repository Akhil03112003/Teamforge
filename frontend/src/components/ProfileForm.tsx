import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError, updateProfile, type Profile, type AvailabilityWindow, type Day } from '../api/client';

const days: Day[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
interface Slot extends AvailabilityWindow { key: string }
const toSlots = (windows: AvailabilityWindow[]): Slot[] => windows.map(window => ({ ...window, key: crypto.randomUUID() }));

export default function ProfileForm({ profile, onSaved, onSessionExpired }: {
  profile: Profile; onSaved: (profile: Profile) => void; onSessionExpired: () => void;
}) {
  const [displayName, setDisplayName] = useState(profile.displayName);
  const [timeZone, setTimeZone] = useState(profile.timeZone);
  const [skills, setSkills] = useState(profile.skills.join(', '));
  const [slots, setSlots] = useState(() => toSlots(profile.collaborationWindows));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [saved, setSaved] = useState(false);
  const summary = useRef<HTMLDivElement>(null);
  useEffect(() => { if (error) summary.current?.focus(); }, [error]);
  const zones = [...new Set(['UTC', timeZone, ...Intl.supportedValuesOf('timeZone')])].sort();

  function changeSlot(key: string, change: Partial<AvailabilityWindow>) {
    setSlots(current => current.map(slot => slot.key === key ? { ...slot, ...change } : slot));
    setSaved(false);
  }
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setSaved(false); setError(null);
    try {
      const result = await updateProfile({
        displayName: displayName.trim(), timeZone,
        skills: skills.split(',').map(skill => skill.trim()).filter(Boolean),
        collaborationWindows: slots.map(({ day, start, end }) => ({ day, start, end })),
      });
      onSaved(result); setSkills(result.skills.join(', ')); setSaved(true);
    } catch (reason) {
      const failure = ApiError.from(reason);
      if (failure.status === 401) onSessionExpired();
      else setError(failure);
    } finally { setBusy(false); }
  }
  return (
    <form className="profile-form" onSubmit={submit} aria-busy={busy}>
      {error && <div className="error-summary" role="alert" tabIndex={-1} ref={summary}>
        <p>{error.message}</p>
        {Object.entries(error.fieldErrors).map(([field, message]) => <p key={field}>{field}: {message}</p>)}
      </div>}
      <fieldset disabled={busy}>
        <legend>About you</legend>
        <div className="profile-grid">
          <div className="field">
            <label htmlFor="profile-name">Display name</label>
            <input id="profile-name" autoComplete="nickname" required maxLength={80}
              value={displayName} onChange={event => { setDisplayName(event.target.value); setSaved(false); }}
              aria-invalid={!!error?.fieldErrors.displayName} />
          </div>
          <div className="field">
            <label htmlFor="time-zone">Time zone</label>
            <select id="time-zone" value={timeZone} onChange={event => { setTimeZone(event.target.value); setSaved(false); }}>
              {zones.map(zone => <option key={zone} value={zone}>{zone}</option>)}
            </select>
          </div>
        </div>
        <div className="field">
          <label htmlFor="skills">Skills</label>
          <input id="skills" value={skills} onChange={event => { setSkills(event.target.value); setSaved(false); }}
            aria-describedby="skills-hint" maxLength={820} />
          <p id="skills-hint" className="note">Up to 20 comma-separated skills, such as java, design, c++ or data science. Each tag can have up to 40 characters.</p>
        </div>
      </fieldset>
      <fieldset disabled={busy}>
        <legend>When can you collaborate?</legend>
        <p className="note" id="availability-hint">Weekly availability in your selected time zone. Use non-overlapping, same-day windows. Availability is optional.</p>
        {slots.length === 0 && <p>No availability added yet.</p>}
        {slots.map((slot, index) => <div className="availability-row" key={slot.key}>
          <div className="field">
            <label htmlFor={'day-' + slot.key}>Day, window {index + 1}</label>
            <select id={'day-' + slot.key} value={slot.day} onChange={event => changeSlot(slot.key, { day: event.target.value as Day })}>
              {days.map(day => <option key={day} value={day}>{day.charAt(0) + day.slice(1).toLowerCase()}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor={'start-' + slot.key}>Start, window {index + 1}</label>
            <input id={'start-' + slot.key} type="time" step="60" required value={slot.start}
              aria-describedby="availability-hint" onChange={event => changeSlot(slot.key, { start: event.target.value })} />
          </div>
          <div className="field">
            <label htmlFor={'end-' + slot.key}>End, window {index + 1}</label>
            <input id={'end-' + slot.key} type="time" step="60" required value={slot.end}
              onChange={event => changeSlot(slot.key, { end: event.target.value })} />
          </div>
          <button className="secondary" type="button" aria-label={'Remove availability window ' + (index + 1)}
            onClick={() => { setSlots(current => current.filter(item => item.key !== slot.key)); setSaved(false); }}>
            Remove
          </button>
        </div>)}
        <button className="secondary" type="button" disabled={busy || slots.length >= 28}
          onClick={() => { setSlots(current => [...current, { key: crypto.randomUUID(), day: 'MONDAY', start: '09:00', end: '17:00' }]); setSaved(false); }}>
          Add availability window
        </button>
      </fieldset>
      <div className="form-actions">
        <button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save profile'}</button>
        <p role="status" aria-live="polite">{saved ? 'Profile saved.' : ''}</p>
      </div>
    </form>
  );
}
