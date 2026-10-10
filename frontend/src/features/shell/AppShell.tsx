import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { clearToken, getToken, usernameOf } from '../../auth/token';
import { FlagLogo } from '../../components/FlagLogo';
import { APP_VERSION } from '../../version';
import styles from './AppShell.module.css';

/** Spec 8.3: top bar (name, Flags / Audit log, user, Sign out), content, footer with the version. */
export function AppShell() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const username = usernameOf(getToken() ?? '');

  function signOut() {
    clearToken();
    queryClient.clear();
    // Push, not replace: Back always lands on an app page, which shows /login again (8.3, D-046).
    navigate('/login');
  }

  return (
    <div className={styles.app}>
      <header className={styles.topbar}>
        <div className={styles.left}>
          <div className={styles.brand}>
            <FlagLogo />
            <span>Feature Flags</span>
          </div>
          <nav aria-label="Main" className={styles.nav}>
            <NavLink
              to="/groups"
              className={({ isActive }) =>
                isActive ? `${styles.link} ${styles.active}` : styles.link
              }
            >
              Flags
            </NavLink>
            <NavLink
              to="/audit"
              className={({ isActive }) =>
                isActive ? `${styles.link} ${styles.active}` : styles.link
              }
            >
              Audit log
            </NavLink>
          </nav>
        </div>
        <div className={styles.right}>
          <div className={styles.user}>
            <span className={styles.avatar} aria-hidden="true">
              {username.slice(0, 1).toUpperCase()}
            </span>
            <span>{username}</span>
          </div>
          <button type="button" className={styles.signOut} onClick={signOut}>
            Sign out
          </button>
        </div>
      </header>
      <div className={styles.content}>
        <Outlet />
      </div>
      <footer className={styles.footer}>v{APP_VERSION}</footer>
    </div>
  );
}
