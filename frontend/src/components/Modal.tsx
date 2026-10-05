import { useId, useRef, type ReactNode } from 'react';
import { useFocusTrap } from './useFocusTrap';
import styles from './Modal.module.css';

interface Props {
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer: ReactNode;
  onSubmit?: () => void;
  /** While the request runs the dialog cannot be closed, so its result is never lost (FF-3). */
  busy?: boolean;
}

/**
 * Spec 8.4: dialog named by its title, with a Close button; traps focus, Escape closes (not while
 * busy).
 */
export function Modal({ title, onClose, children, footer, onSubmit, busy = false }: Props) {
  const ref = useRef<HTMLFormElement>(null);
  const titleId = useId();
  const close = () => {
    if (!busy) onClose();
  };
  useFocusTrap(ref, close);
  return (
    <div className={styles.overlay}>
      <form
        ref={ref}
        role="dialog"
        aria-modal="true"
        tabIndex={-1}
        aria-labelledby={titleId}
        className={styles.dialog}
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          onSubmit?.();
        }}
      >
        <div className={styles.header}>
          <h2 id={titleId} className={styles.title}>
            {title}
          </h2>
          <button
            type="button"
            aria-label="Close"
            className={styles.close}
            onClick={close}
            disabled={busy}
          >
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="#3B424C"
              strokeWidth="2"
              strokeLinecap="round"
              aria-hidden="true"
            >
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>
        <div className={styles.body}>{children}</div>
        <div className={styles.footer}>{footer}</div>
      </form>
    </div>
  );
}
