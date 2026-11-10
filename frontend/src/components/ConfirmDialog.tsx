import { useEffect, useRef, type ReactNode } from 'react';

export default function ConfirmDialog({ title, children, busy, confirmLabel, confirm, cancel }: {
  title: string; children: ReactNode; busy: boolean; confirmLabel: string; confirm: () => void; cancel: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const cancelButton = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    const element = dialog.current;
    element?.showModal(); cancelButton.current?.focus();
    return () => { element?.close(); if (opener?.isConnected) opener.focus(); };
  }, []);
  return <dialog ref={dialog} aria-labelledby="confirm-title" aria-describedby="confirm-description" onKeyDown={event => {
    if (event.key !== 'Tab') return;
    const buttons = [...event.currentTarget.querySelectorAll<HTMLButtonElement>('button:not(:disabled)')];
    const first = buttons[0], last = buttons.at(-1);
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
  }} onCancel={event => { event.preventDefault(); if (!busy) cancel(); }}>
    <h2 id="confirm-title">{title}</h2><div id="confirm-description">{children}</div>
    <div className="form-actions"><button className="secondary" ref={cancelButton} type="button" disabled={busy} onClick={cancel}>Cancel</button><button className="danger" type="button" disabled={busy} onClick={confirm}>{busy ? 'Updating…' : confirmLabel}</button></div>
  </dialog>;
}
