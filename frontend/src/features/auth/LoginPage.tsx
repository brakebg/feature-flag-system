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
      <div className={styles.column}>
        <div className={styles.brand}>
          <span className={styles.logo}>
            <FlagLogo size={20} />
          </span>
          <span>Feature Flags</span>
        </div>
        <form className={styles.card} onSubmit={submit} noValidate>
          <div className={styles.heading}>
            <h1 className={styles.title}>Sign in</h1>
            <p className={styles.subtitle}>Use your admin credentials to manage flags.</p>
          </div>
          {params.get('expired') === '1' && (
            <div role="status" className={styles.info}>
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                aria-hidden="true"
                className={styles.infoIcon}
              >
                <circle cx="12" cy="12" r="9" />
                <path d="M12 11v5M12 8h.01" />
              </svg>
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
              {error}
            </div>
          )}
          <button type="submit" className={styles.submit} disabled={busy} aria-busy={busy}>
            {busy && <Spinner />}
            Sign in
          </button>
        </form>
      </div>
    </div>
  );
}
