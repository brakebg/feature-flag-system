import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { login } from '../../api/auth';
import { getToken, isExpired, setToken } from '../../auth/token';
import { FlagLogo } from '../../components/FlagLogo';
import { Spinner } from '../../components/Spinner';
import { loginErrorMessage } from './loginMessages';
import styles from './LoginPage.module.css';

/** Spec 8.2: sign in with the admin credentials. */
export function LoginPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const token = getToken();
  if (token && !isExpired(token)) {
    return <Navigate to="/" replace />;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const result = await login(username, password);
      setToken(result.accessToken);
      navigate('/groups', { replace: true });
    } catch (e) {
      setError(loginErrorMessage(e));
      setBusy(false);
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.art} aria-hidden="true">
        <span className={styles.bigCircle} />
        <span className={styles.half} />
        <span className={styles.square} />
        <span className={styles.ring} />
        <span className={styles.switchOn} />
        <span className={styles.switchOff} />
        <span className={styles.dots} />
        <span className={styles.quarter} />
      </div>
      <div className={styles.side}>
        <div className={styles.column}>
          <div className={styles.brand}>
            <FlagLogo size={30} />
            <span>Feature Flags</span>
          </div>
          <form className={styles.form} onSubmit={submit} noValidate>
            <div className={styles.heading}>
              <h1 className={styles.title}>Sign in</h1>
              <p className={styles.subtitle}>Use your admin credentials to manage flags.</p>
            </div>
            {params.get('expired') === '1' && (
              <div role="status" className={styles.info}>
                <span className={styles.infoDot} aria-hidden="true">
                  <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true">
                    <rect x="6" y="2" width="2" height="2" rx="1" fill="#ffffff" />
                    <rect x="6" y="5.5" width="2" height="6.5" rx="1" fill="#ffffff" />
                  </svg>
                </span>
                <span>Your session expired. Please sign in again.</span>
              </div>
            )}
            <div className={styles.field}>
              <label htmlFor="username" className={styles.label}>
                Username
              </label>
              <input
                id="username"
                type="text"
                autoComplete="username"
                className={styles.input}
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </div>
            <div className={styles.field}>
              <label htmlFor="password" className={styles.label}>
                Password
              </label>
              <input
                id="password"
                type="password"
                autoComplete="current-password"
                className={styles.input}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
            {error && (
              <div role="alert" className={styles.error}>
                <span className={styles.errorGlyph} aria-hidden="true" />
                {error}
              </div>
            )}
            <button type="submit" className={styles.submit} disabled={busy} aria-busy={busy}>
              <span className={styles.submitText}>
                {busy && <Spinner />}
                Sign in
              </span>
              <span className={styles.submitIcon} aria-hidden="true">
                <svg
                  aria-hidden="true"
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2.4"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M5 12h14M13 6l6 6-6 6" />
                </svg>
              </span>
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
