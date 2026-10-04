import { useId, type InputHTMLAttributes, type ReactNode } from 'react';
import styles from './TextField.module.css';

interface Props extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  label: ReactNode;
  error?: string;
  help?: ReactNode;
  prefix?: string;
  multiline?: boolean;
  mono?: boolean;
}

/**
 * Spec 8.5: a labelled field; an invalid field gets `aria-invalid="true"` and its error text is
 * linked with `aria-describedby`.
 */
export function TextField({
  label,
  error,
  help,
  prefix,
  multiline = false,
  mono = false,
  className,
  ...rest
}: Props) {
  const id = useId();
  const errorId = `${id}-error`;
  const helpId = `${id}-help`;
  const describedBy =
    [error ? errorId : null, help ? helpId : null].filter(Boolean).join(' ') || undefined;
  const common = {
    id,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': describedBy,
    className: [styles.input, mono ? styles.mono : '', prefix ? styles.joined : ''].join(' '),
  };
  return (
    <div className={[styles.field, className].filter(Boolean).join(' ')}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      {multiline ? (
        <textarea
          {...common}
          rows={3}
          value={rest.value}
          onChange={rest.onChange as unknown as React.ChangeEventHandler<HTMLTextAreaElement>}
          onBlur={rest.onBlur as unknown as React.FocusEventHandler<HTMLTextAreaElement>}
          name={rest.name}
          readOnly={rest.readOnly}
        />
      ) : prefix ? (
        <div className={`${styles.prefixed} ${error ? styles.invalid : ''}`}>
          <span className={styles.prefix}>{prefix}</span>
          <input {...rest} {...common} />
        </div>
      ) : (
        <input {...rest} {...common} />
      )}
      {error && (
        <span id={errorId} className={styles.error}>
          {error}
        </span>
      )}
      {help && (
        <span id={helpId} className={styles.help}>
          {help}
        </span>
      )}
    </div>
  );
}
