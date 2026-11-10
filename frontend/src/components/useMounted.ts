import { useEffect, useRef } from 'react';

/** Prevent a late response from an old participant's UI from changing the next session. */
export function useMounted() {
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  return mounted;
}
