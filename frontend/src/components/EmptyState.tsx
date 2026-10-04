import type { ReactNode } from 'react';
import styles from './EmptyState.module.css';

/** A message with an optional action, for empty lists (spec 8.4). */
export function EmptyState({ text, action }: { text: string; action?: ReactNode }) {
  return (
    <div className={styles.empty}>
      <p className={styles.text}>{text}</p>
      {action}
    </div>
  );
}
