import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Spinner } from './Spinner';
import styles from './Button.module.css';

type Variant = 'primary' | 'soft' | 'secondary' | 'danger' | 'danger-outline' | 'dark';

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  busy?: boolean;
  /** Decorative icon in a circle after the text (spec 005 4.4); outside the accessible name. */
  icon?: ReactNode;
  /** 48 px high, as the dialog footers of designs 3, 4 and 6. */
  large?: boolean;
}

/** Spec 8.7: every action control is a button. Spec 005 4.4: pills. */
export function Button({
  variant = 'secondary',
  busy = false,
  icon,
  large = false,
  children,
  className,
  ...rest
}: Props) {
  return (
    <button
      type="button"
      {...rest}
      aria-busy={busy || undefined}
      className={[
        styles.button,
        styles[variant],
        large ? styles.large : null,
        icon && !busy ? styles.withIcon : null,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {busy && <Spinner />}
      {children}
      {/* Design 6: while busy the spinner and text are centred and the icon is not shown. */}
      {icon && !busy && (
        <span className={styles.icon} aria-hidden="true">
          {icon}
        </span>
      )}
    </button>
  );
}
