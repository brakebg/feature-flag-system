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
  // FF-2: pages are offsets, so new events can push an event already shown onto the next page.
  const seen = new Set<number>();
  const events = (audit.data?.pages.flatMap((p) => p.content) ?? []).filter(
    (e) => !seen.has(e.id) && seen.add(e.id),
  );

  return (
    <main className={styles.main}>
      <div className={styles.hero}>
        <span className={`${styles.shape} ${styles.circle}`} aria-hidden="true" />
        <span className={`${styles.shape} ${styles.half}`} aria-hidden="true" />
        <span className={`${styles.shape} ${styles.square}`} aria-hidden="true" />
        <div className={styles.heading}>
          <h1 className={styles.title}>Audit log</h1>
          <p className={styles.subtitle}>Every change to groups and flags, newest first.</p>
        </div>
        <div className={styles.search}>
          <label htmlFor="target-filter" className="visually-hidden">
            Filter by target key
          </label>
          <SearchIcon />
          <input
            id="target-filter"
            type="search"
            placeholder="Filter by target key, e.g. orders."
            className={styles.searchInput}
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
        </div>
      </div>
      <section className={styles.card}>
        <div
          role="table"
          aria-label="Audit events"
          aria-busy={audit.isPending}
          className={styles.table}
        >
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
          <div role="rowgroup" className={styles.body}>
            {events.map((e) => (
              <div role="row" key={e.id} className={styles.row}>
                <div role="cell" className={`${styles.time} ${styles.hideSm}`}>
                  <span className={styles.absolute}>{localTime(e.occurredAt)}</span>
                  <span className={styles.ago}>{relativeTime(e.occurredAt)}</span>
                </div>
                <div role="cell" className={`${styles.soft} ${styles.hideSm}`}>
                  {e.actor}
                </div>
                <div role="cell">
                  <span className={`${styles.label} ${styles[actionTone(e.action)]}`}>
                    <span className={styles.glyph} aria-hidden="true" />
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
              variant="dark"
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
