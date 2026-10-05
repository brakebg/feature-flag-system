import {
  useId,
  type InputHTMLAttributes,
  type ReactNode,
  type TextareaHTMLAttributes,
} from 'react';
import styles from './TextField.module.css';

interface Common {
  label: ReactNode;
  error?: string;
  help?: ReactNode;
  mono?: boolean;
}

type InputProps = Common &
  Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'prefix'> & {
    multiline?: false;
    prefix?: string;
  };

type AreaProps = Common &
  Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'id'> & {
    multiline: true;
  };

/** Props of TextField itself; everything else goes to the input or textarea element. */
const OWN_KEYS = ['label', 'error', 'help', 'mono', 'multiline', 'className'];

function without<T>(props: object, keys: string[]): T {
  return Object.fromEntries(Object.entries(props).filter(([k]) => !keys.includes(k))) as T;
}

/**
 * Spec 8.5: a labelled field; an invalid field gets `aria-invalid="true"` and its error text is
 * linked with `aria-describedby`.
 */
export function TextField(props: InputProps | AreaProps) {
  const id = useId();
  const errorId = `${id}-error`;
  const helpId = `${id}-help`;
  const { label, error, help, mono = false, className } = props;
  const describedBy =
    [error ? errorId : null, help ? helpId : null].filter(Boolean).join(' ') || undefined;
  const common = {
    id,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': describedBy,
  };

  let control: ReactNode;
  if (props.multiline) {
    const rest: TextareaHTMLAttributes<HTMLTextAreaElement> = without(props, OWN_KEYS);
    control = (
      <textarea
        rows={3}
        {...rest}
        {...common}
        className={[styles.input, mono ? styles.mono : ''].join(' ')}
      />
    );
  } else {
    const prefix = props.prefix;
    const rest: InputHTMLAttributes<HTMLInputElement> = without(props, [...OWN_KEYS, 'prefix']);
    const input = (
      <input
        {...rest}
        {...common}
        className={[styles.input, mono ? styles.mono : '', prefix ? styles.joined : ''].join(' ')}
      />
    );
    control = prefix ? (
      <div className={`${styles.prefixed} ${error ? styles.invalid : ''}`}>
        <span className={styles.prefix}>{prefix}</span>
        {input}
      </div>
    ) : (
      input
    );
  }

  return (
    <div className={[styles.field, className].filter(Boolean).join(' ')}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      {control}
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
