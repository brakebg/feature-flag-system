/**
 * Spec 005 4.5: the share of flags that are on, for the group pie. Half up with integer maths:
 * floor((200 × enabled + total) / (2 × total))%, and 0% when the group has no flags.
 */
export function pieShare(enabled: number, total: number): string {
  if (total === 0) return '0%';
  return `${Math.floor((200 * enabled + total) / (2 * total))}%`;
}
