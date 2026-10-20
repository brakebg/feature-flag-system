import { Link } from 'react-router-dom';
import styles from './NotFoundPage.module.css';

/** Spec 8.1: heading `Page not found` and a link `Back to flags`. Look: design 6. */
export function NotFoundPage() {
  return (
    <main className={styles.page}>
      <div className={styles.card}>
        <span className={styles.ring} aria-hidden="true" />
        <span className={styles.quarter} aria-hidden="true" />
        <h1 className={styles.title}>Page not found</h1>
        <Link to="/groups" className={styles.link}>
          Back to flags
        </Link>
      </div>
    </main>
  );
}
