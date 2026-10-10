import type { Flag } from '../../api/types';

export type StatusFilter = 'all' | 'on' | 'off';

/** Spec 8.4 flag search and status filter (client-side). */
export function filterFlags(flags: Flag[], text: string, status: StatusFilter): Flag[] {
  const needle = text.toLowerCase();
  return flags.filter((f) => {
    const matchesText =
      !needle ||
      f.key.toLowerCase().includes(needle) ||
      (f.description ?? '').toLowerCase().includes(needle);
    const matchesStatus = status === 'all' || (status === 'on' ? f.enabled : !f.enabled);
    return matchesText && matchesStatus;
  });
}
