import type { GroupSummary } from '../../api/types';

/** Spec 8.4: a group is shown when the text is a case-insensitive substring of its key or name. */
export function filterGroups(groups: GroupSummary[], text: string): GroupSummary[] {
  const needle = text.toLowerCase();
  if (!needle) return groups;
  return groups.filter(
    (g) => g.key.toLowerCase().includes(needle) || g.name.toLowerCase().includes(needle),
  );
}
