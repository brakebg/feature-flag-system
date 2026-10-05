import { useState } from 'react';
import type { Flag, GroupDetail } from '../../api/types';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { useToast } from '../../components/toastContext';
import { relativeTime } from '../../lib/time';
import { DeleteFlagDialog } from '../flags/DeleteFlagDialog';
import { EditFlagDialog, NewFlagDialog } from '../flags/FlagDialog';
import { FlagTable } from '../flags/FlagTable';
import { filterFlags, type StatusFilter } from '../flags/flagFilter';
import { DeleteGroupDialog } from './DeleteGroupDialog';
import { EditGroupDialog } from './GroupDialog';
import { PlusIcon, SearchIcon } from './GroupList';
import styles from './GroupPanel.module.css';

type Dialog =
  | { kind: 'none' }
  | { kind: 'edit-group' }
  | { kind: 'delete-group' }
  | { kind: 'new-flag' }
  | { kind: 'edit-flag'; flag: Flag }
  | { kind: 'delete-flag'; flag: Flag };

const FILTERS: [StatusFilter, string][] = [
  ['all', 'All'],
  ['on', 'On'],
  ['off', 'Off'],
];

/** Spec 8.4 right pane: the selected group's header and its flags. */
export function GroupPanel({ group, onDeleted }: { group: GroupDetail; onDeleted: () => void }) {
  const toast = useToast();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<StatusFilter>('all');
  const [dialog, setDialog] = useState<Dialog>({ kind: 'none' });
  const close = () => setDialog({ kind: 'none' });
  const enabled = group.flags.filter((f) => f.enabled).length;
  const total = group.flags.length;
  const shown = filterFlags(group.flags, search, status);
  const count = `${enabled} of ${total} flags on`;

  async function copyKey() {
    try {
      await navigator.clipboard.writeText(group.key);
    } catch {
      toast.error('Could not copy the group key');
    }
  }

  return (
    <main className={styles.main}>
      <div className={styles.header}>
        <div className={styles.heading}>
          <div className={styles.titleRow}>
            <h1 className={styles.title}>{group.name}</h1>
            <span className={styles.keyChip}>
              {group.key}
              <button
                type="button"
                aria-label="Copy group key"
                className={styles.copy}
                onClick={() => void copyKey()}
              >
                <svg
                  width="14"
                  height="14"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="#59616C"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden="true"
                >
                  <rect x="9" y="9" width="11" height="11" rx="2" />
                  <path d="M5 15V5a2 2 0 0 1 2-2h10" />
                </svg>
              </button>
            </span>
          </div>
          <p className={styles.description}>
            {group.description ? `${group.description} · ${count}` : count}
          </p>
          <p className={styles.meta}>
            Created by <span className={styles.who}>{group.createdBy}</span> · Updated by{' '}
            <span className={styles.who}>{group.updatedBy}</span>, {relativeTime(group.updatedAt)}
          </p>
        </div>
        <div className={styles.headerActions}>
          <Button onClick={() => setDialog({ kind: 'edit-group' })}>Edit group</Button>
          <Button variant="danger-outline" onClick={() => setDialog({ kind: 'delete-group' })}>
            Delete group
          </Button>
        </div>
      </div>

      <section aria-label="Flags" className={styles.card}>
        <div className={styles.toolbar}>
          <div className={styles.filters}>
            <div className={styles.search}>
              <label htmlFor="flag-search" className="visually-hidden">
                Search flags
              </label>
              <input
                id="flag-search"
                type="search"
                placeholder="Search flags in this group"
                className={styles.searchInput}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
              <SearchIcon />
            </div>
            <div role="group" aria-label="Filter by status" className={styles.segments}>
              {FILTERS.map(([value, label]) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={status === value}
                  className={`${styles.segment} ${status === value ? styles.segmentOn : ''}`}
                  onClick={() => setStatus(value)}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
          <Button
            variant="primary"
            icon={<PlusIcon />}
            onClick={() => setDialog({ kind: 'new-flag' })}
          >
            New flag
          </Button>
        </div>
        {total === 0 ? (
          <EmptyState
            text="No flags in this group"
            action={
              <Button variant="primary" onClick={() => setDialog({ kind: 'new-flag' })}>
                Add flag
              </Button>
            }
          />
        ) : (
          <>
            <FlagTable
              group={group}
              flags={shown}
              onEdit={(flag) => setDialog({ kind: 'edit-flag', flag })}
              onDelete={(flag) => setDialog({ kind: 'delete-flag', flag })}
            />
            {shown.length === 0 && <EmptyState text="No flags match this filter." />}
          </>
        )}
      </section>

      {dialog.kind === 'edit-group' && <EditGroupDialog group={group} onClose={close} />}
      {dialog.kind === 'delete-group' && (
        <DeleteGroupDialog group={group} onClose={close} onDeleted={onDeleted} />
      )}
      {dialog.kind === 'new-flag' && <NewFlagDialog group={group} onClose={close} />}
      {dialog.kind === 'edit-flag' && <EditFlagDialog flag={dialog.flag} onClose={close} />}
      {dialog.kind === 'delete-flag' && <DeleteFlagDialog flag={dialog.flag} onClose={close} />}
    </main>
  );
}
