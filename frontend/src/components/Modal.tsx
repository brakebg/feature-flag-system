import { useId, useRef, type ReactNode } from 'react';
import { useFocusTrap } from './useFocusTrap';
import styles from './Modal.module.css';

interface Props {
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer: ReactNode;
  onSubmit?: () => void;
}

/** Spec 8.4: dialog named by its title, with a Close button; traps focus, Escape closes. */
export function Modal({ title, onClose, children, footer, onSubmit }: Props) {
  const ref = useRef<HTMLFormElement>(null);
  const titleId = useId();
  useFocusTrap(ref, onClose);
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
          <button type="button" aria-label="Close" className={styles.close} onClick={onClose}>
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
