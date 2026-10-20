import type { ReactNode } from 'react';
import styles from './EmptyState.module.css';

/**
 * A message with an optional action, for empty lists (spec 8.4). With an action it shows the
 * decorative ring and half circle of design 6.
 */
export function EmptyState({ text, action }: { text: string; action?: ReactNode }) {
  return (
    <div className={`${styles.empty} ${action ? '' : styles.small}`}>
      {action && (
        <span className={styles.shapes} aria-hidden="true">
          <span className={styles.ring} />
          <span className={styles.half} />
        </span>
      )}
      <p className={styles.text}>{text}</p>
      {action}
    </div>
  );
}
