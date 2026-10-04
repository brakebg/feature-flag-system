import styles from './Spinner.module.css';

/** Small busy indicator (decorative; the button carries aria-busy). */
export function Spinner() {
  return <span className={styles.spinner} aria-hidden="true" />;
}
