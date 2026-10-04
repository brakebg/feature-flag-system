import { Link } from 'react-router-dom';
import styles from './NotFoundPage.module.css';

/** Spec 8.1: heading `Page not found` and a link `Back to flags`. */
export function NotFoundPage() {
  return (
    <main className={styles.page}>
      <h1 className={styles.title}>Page not found</h1>
      <Link to="/groups" className={styles.link}>
        Back to flags
      </Link>
    </main>
  );
}
