import styles from './OptionalLabel.module.css';

/** Design 3: "(optional)" in regular weight and muted colour inside a label (DC-2). */
export function OptionalLabel({ text }: { text: string }) {
  return (
    <>
      {text} <span className={styles.optional}>(optional)</span>
    </>
  );
}
