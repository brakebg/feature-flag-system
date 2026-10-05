import { useState } from 'react';
import type { GroupSummary } from '../../api/types';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { filterGroups } from './groupFilter';
import styles from './GroupList.module.css';

export function PlusIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}

interface Props {
  groups: GroupSummary[];
  selectedId: string | undefined;
  onSelect: (id: string) => void;
  onNew: () => void;
}

/** Spec 8.4 left pane: search, the group list (`nav` "Flag groups") and New group. */
export function GroupList({ groups, selectedId, onSelect, onNew }: Props) {
  const [search, setSearch] = useState('');
  const shown = filterGroups(groups, search);
  return (
    <aside aria-label="Groups" className={styles.pane}>
      <div className={styles.header}>
        <h2 className={styles.title}>Groups</h2>
        <Button variant="ghost" icon={<PlusIcon />} onClick={onNew}>
          New group
        </Button>
      </div>
      <div className={styles.search}>
        <label htmlFor="group-search" className="visually-hidden">
          Search groups
        </label>
        <input
          id="group-search"
          type="search"
          placeholder="Search groups"
          className={styles.searchInput}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <SearchIcon />
      </div>
      {groups.length === 0 ? (
        <EmptyState
          text="No groups yet"
          action={
            <Button variant="primary" onClick={onNew}>
              Create your first group
            </Button>
          }
        />
      ) : shown.length === 0 ? (
        <EmptyState text="No groups match your search" />
      ) : (
        <nav aria-label="Flag groups" className={styles.list}>
          {shown.map((g) => {
            const active = g.id === selectedId;
            return (
              <button
                key={g.id}
                type="button"
                aria-current={active ? 'true' : undefined}
                className={`${styles.item} ${active ? styles.active : ''}`}
                onClick={() => onSelect(g.id)}
              >
                <span className={styles.names}>
                  <span className={styles.name}>{g.name}</span>
                  <span className={styles.key}>{g.key}</span>
                </span>
                <span className={styles.badge}>{`${g.enabledCount}/${g.flagCount}`}</span>
              </button>
            );
          })}
        </nav>
      )}
    </aside>
  );
}

export function SearchIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="#59616C"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
      className="search-icon"
    >
      <circle cx="11" cy="11" r="7" />
      <path d="M20 20l-3.5-3.5" />
    </svg>
  );
}
