// Spec 8.4, 8.6: the wording of relative times is not specified; this is a short English form.
const UNITS: [number, string][] = [
  [60, 'second'],
  [60, 'minute'],
  [24, 'hour'],
  [30, 'day'],
  [12, 'month'],
  [Number.POSITIVE_INFINITY, 'year'],
];

export function relativeTime(iso: string, now: number = Date.now()): string {
  let value = Math.max(0, Math.round((now - Date.parse(iso)) / 1000));
  if (value < 10) return 'just now';
  for (const [size, unit] of UNITS) {
    if (value < size) return `${value} ${unit}${value === 1 ? '' : 's'} ago`;
    value = Math.floor(value / size);
  }
  return '';
}

/** Absolute local time for the audit table (format not specified). */
export function localTime(iso: string): string {
  return new Date(iso).toLocaleString();
}
