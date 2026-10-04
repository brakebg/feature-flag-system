import { useLocation, useNavigate } from 'react-router-dom';

/** Shows the current location so tests can assert redirects. */
export function LocationProbe() {
  const location = useLocation();
  const navigate = useNavigate();
  return (
    <>
      <div data-testid="location">{location.pathname + location.search}</div>
      <button type="button" data-testid="history-back" onClick={() => navigate(-1)}>
        history back
      </button>
    </>
  );
}
