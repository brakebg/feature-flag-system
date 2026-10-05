import { useIsMutating } from '@tanstack/react-query';
import type { Flag, GroupDetail } from '../../api/types';
import { Toggle } from '../../components/Toggle';
import { toggleKey, useToggleFlag } from '../../hooks/queries';
import { relativeTime } from '../../lib/time';
import styles from './FlagTable.module.css';

interface Props {
  group: GroupDetail;
  flags: Flag[];
  onEdit: (flag: Flag) => void;
  onDelete: (flag: Flag) => void;
}

/** Spec 8.4 flags table (table semantics per 8.7). */
export function FlagTable({ group, flags, onEdit, onDelete }: Props) {
  return (
    <div role="table" aria-label="Flags" className={styles.table}>
      <div role="rowgroup">
        <div role="row" className={`${styles.row} ${styles.head}`}>
          <div role="columnheader">Key</div>
          <div role="columnheader">Description</div>
          <div role="columnheader">Status</div>
          <div role="columnheader" className={styles.hideSm}>
            Created by
          </div>
          <div role="columnheader" className={styles.hideSm}>
            Updated
          </div>
          <div role="columnheader" className={styles.actionsHead}>
            Actions
          </div>
        </div>
      </div>
      <div role="rowgroup">
        {flags.map((flag) => (
          <FlagRow key={flag.id} group={group} flag={flag} onEdit={onEdit} onDelete={onDelete} />
        ))}
      </div>
    </div>
  );
}

function FlagRow({
  group,
  flag,
  onEdit,
  onDelete,
}: {
  group: GroupDetail;
  flag: Flag;
  onEdit: (flag: Flag) => void;
  onDelete: (flag: Flag) => void;
}) {
  const toggle = useToggleFlag(group.id, flag);
  // Spec 8.5: disabled while its request runs, also after the row is shown again.
  const pending = useIsMutating({ mutationKey: toggleKey(flag.id) }) > 0;

  function change(enabled: boolean) {
    toggle.mutate(enabled);
  }

  return (
    <div role="row" className={styles.row}>
      <div role="cell" className={styles.keyCell}>
        <span className={styles.key}>{flag.key}</span>
        <span className={styles.fullKey}>{flag.fullKey}</span>
      </div>
      <div role="cell" className={styles.description}>
        {flag.description ?? ''}
      </div>
      <div role="cell" className={styles.status}>
        <Toggle
          checked={flag.enabled}
          onChange={change}
          label={`Toggle ${flag.fullKey}`}
          disabled={pending}
        />
        <span className={flag.enabled ? styles.on : styles.off}>{flag.enabled ? 'On' : 'Off'}</span>
      </div>
      <div role="cell" className={`${styles.muted} ${styles.hideSm}`}>
        {flag.createdBy}
      </div>
      <div role="cell" className={`${styles.updated} ${styles.hideSm}`}>
        <span className={styles.muted}>{relativeTime(flag.updatedAt)}</span>
        <span className={styles.by}>by {flag.updatedBy}</span>
      </div>
      <div role="cell" className={styles.actions}>
        <button
          type="button"
          aria-label={`Edit ${flag.fullKey}`}
          className={styles.icon}
          onClick={() => onEdit(flag)}
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="#3B424C"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M4 20h4L19 9l-4-4L4 16v4z" />
          </svg>
        </button>
        <button
          type="button"
          aria-label={`Delete ${flag.fullKey}`}
          className={styles.icon}
          onClick={() => onDelete(flag)}
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="#A8231A"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13" />
          </svg>
        </button>
      </div>
    </div>
  );
}
