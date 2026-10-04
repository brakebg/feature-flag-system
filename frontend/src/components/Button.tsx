import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Spinner } from './Spinner';
import styles from './Button.module.css';

type Variant = 'primary' | 'secondary' | 'danger' | 'danger-outline' | 'ghost';

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  busy?: boolean;
  icon?: ReactNode;
}

/** Spec 8.7: every action control is a button. */
export function Button({
  variant = 'secondary',
  busy = false,
  icon,
  children,
  className,
  ...rest
}: Props) {
  return (
    <button
      type="button"
      {...rest}
      aria-busy={busy || undefined}
      className={[styles.button, styles[variant], className].filter(Boolean).join(' ')}
    >
      {busy ? <Spinner /> : icon}
      {children}
    </button>
  );
}
