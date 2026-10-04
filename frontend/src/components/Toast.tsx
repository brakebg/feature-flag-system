import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { TOAST_MS, ToastContext, type ToastApi } from './toastContext';
import styles from './Toast.module.css';

type Kind = 'success' | 'error';
interface ToastItem {
  id: number;
  kind: Kind;
  text: string;
}

/** Spec 8.5: toasts for 3 s; `role="status"` for success, `role="alert"` for errors. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const next = useRef(1);
  const push = useCallback((kind: Kind, text: string) => {
    const id = next.current++;
    setItems((list) => [...list, { id, kind, text }]);
  }, []);
  const remove = useCallback(
    (id: number) => setItems((list) => list.filter((t) => t.id !== id)),
    [],
  );
  const api = useMemo<ToastApi>(
    () => ({ success: (t) => push('success', t), error: (t) => push('error', t) }),
    [push],
  );
  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className={styles.stack}>
        {items.map((t) => (
          <Toast key={t.id} item={t} onDone={remove} />
        ))}
      </div>
    </ToastContext.Provider>
  );
}

function Toast({ item, onDone }: { item: ToastItem; onDone: (id: number) => void }) {
  useEffect(() => {
    const timer = window.setTimeout(() => onDone(item.id), TOAST_MS);
    return () => window.clearTimeout(timer);
  }, [item.id, onDone]);
  return (
    <div
      role={item.kind === 'success' ? 'status' : 'alert'}
      className={`${styles.toast} ${styles[item.kind]}`}
    >
      {item.text}
    </div>
  );
}
