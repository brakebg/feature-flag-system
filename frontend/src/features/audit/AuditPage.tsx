import { useState } from 'react';
import { Button } from '../../components/Button';
import { useAudit } from '../../hooks/queries';
import { loadErrorText } from '../../lib/errors';
import { localTime, relativeTime } from '../../lib/time';
import { SearchIcon } from '../groups/GroupList';
import { ACTION_LABELS, actionTone, auditDetails } from './auditFormat';
import styles from './AuditPage.module.css';

/** Spec 8.6 audit log: newest first, filter on target key, Load more (50 per page). */
export function AuditPage() {
  const [filter, setFilter] = useState('');
  const audit = useAudit(filter);
  const events = audit.data?.pages.flatMap((p) => p.content) ?? [];

  return (
    <main className={styles.main}>
      <div className={styles.header}>
        <div className={styles.heading}>
          <h1 className={styles.title}>Audit log</h1>
          <p className={styles.subtitle}>Every change to groups and flags, newest first.</p>
        </div>
        <div className={styles.search}>
          <label htmlFor="target-filter" className="visually-hidden">
            Filter by target key
          </label>
          <input
            id="target-filter"
            type="search"
            placeholder="Filter by target key, e.g. orders."
            className={styles.searchInput}
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
          <SearchIcon />
        </div>
      </div>
      <section className={styles.card}>
        <div role="table" aria-label="Audit events" aria-busy={audit.isPending}>
          <div role="rowgroup">
            <div role="row" className={`${styles.row} ${styles.head}`}>
              <div role="columnheader" className={styles.hideSm}>
                Time
              </div>
              <div role="columnheader" className={styles.hideSm}>
                Actor
              </div>
              <div role="columnheader">Action</div>
              <div role="columnheader">Target</div>
              <div role="columnheader">Details</div>
            </div>
          </div>
          <div role="rowgroup">
            {events.map((e) => (
              <div role="row" key={e.id} className={styles.row}>
                <div role="cell" className={`${styles.time} ${styles.hideSm}`}>
                  <span>{localTime(e.occurredAt)}</span>
                  <span className={styles.ago}>{relativeTime(e.occurredAt)}</span>
                </div>
                <div role="cell" className={styles.hideSm}>
                  {e.actor}
                </div>
                <div role="cell">
                  <span className={`${styles.label} ${styles[actionTone(e.action)]}`}>
                    {ACTION_LABELS[e.action] ?? e.action}
                  </span>
                </div>
                <div role="cell" className={styles.target}>
                  {e.targetKey}
                </div>
                <div role="cell" className={styles.details}>
                  {auditDetails(e)}
                </div>
              </div>
            ))}
          </div>
        </div>
        {audit.isError && (
          <p role="alert" className={styles.error}>
            {loadErrorText(audit.error, 'Could not load audit events')}
          </p>
        )}
        {audit.hasNextPage && (
          <div className={styles.more}>
            <Button
              busy={audit.isFetchingNextPage}
              disabled={audit.isFetchingNextPage}
              onClick={() => void audit.fetchNextPage()}
            >
              Load more
            </Button>
          </div>
        )}
      </section>
    </main>
  );
}
