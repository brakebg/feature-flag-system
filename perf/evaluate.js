// Gate 13 (spec 9.2, 11.3): Evaluation API load, 200 req/s for 60 s, split equally over
// the three endpoints, existing keys only. Fails on p95 >= 50 ms or any error.
// The cache hit rate (AC-CACHE-9) is computed by scripts/perf.sh from the metric deltas.
import http from 'k6/http';
import { check } from 'k6';

const API = __ENV.API_URL;
const TOKEN = __ENV.CLIENT_TOKEN;
const GROUP = __ENV.GROUP_KEY;
const FLAGS = (__ENV.FLAG_KEYS || '').split(',');

export const options = {
  scenarios: {
    evaluate: {
      executor: 'constant-arrival-rate',
      rate: 200,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<50'],
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
  },
};

const params = { headers: { Authorization: `Bearer ${TOKEN}` } };

export default function () {
  const i = __ITER % 3;
  const flag = FLAGS[__ITER % FLAGS.length];
  let url;
  if (i === 0) url = `${API}/api/v1/evaluate/flags`;
  else if (i === 1) url = `${API}/api/v1/evaluate/groups/${GROUP}`;
  else url = `${API}/api/v1/evaluate/flags/${GROUP}/${flag}`;
  const res = http.get(url, params);
  check(res, { 'status 200': (r) => r.status === 200 });
}
