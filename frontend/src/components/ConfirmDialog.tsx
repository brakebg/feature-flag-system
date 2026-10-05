import { useId, useRef, type ReactNode } from 'react';
import { Button } from './Button';
import { useFocusTrap } from './useFocusTrap';
import styles from './ConfirmDialog.module.css';

interface Props {
  title: string;
  text: string;
  confirmLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
  busy?: boolean;
  confirmDisabled?: boolean;
  children?: ReactNode;
}

/** Spec 8.5: confirm dialogs are alertdialogs named by their title. */
export function ConfirmDialog({
  title,
  text,
  confirmLabel,
  onConfirm,
  onCancel,
  busy = false,
  confirmDisabled = false,
  children,
}: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const textId = useId();
  useFocusTrap(ref, () => {
    if (!busy) onCancel();
  });
  return (
    <div className={styles.overlay}>
      <div
        ref={ref}
        role="alertdialog"
        aria-modal="true"
        tabIndex={-1}
        aria-labelledby={titleId}
        aria-describedby={textId}
        className={styles.dialog}
      >
        <div className={styles.top}>
          <span className={styles.icon} aria-hidden="true">
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="#A8231A"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M12 9v4M12 17h.01" />
              <path d="M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" />
            </svg>
          </span>
          <div className={styles.heading}>
            <h2 id={titleId} className={styles.title}>
              {title}
            </h2>
            <p id={textId} className={styles.text}>
              {text}
            </p>
          </div>
        </div>
        {children && <div className={styles.body}>{children}</div>}
        <div className={styles.footer}>
          <Button onClick={onCancel} data-autofocus>
            Cancel
          </Button>
          <Button
            variant="danger"
            onClick={onConfirm}
            disabled={confirmDisabled || busy}
            busy={busy}
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}
