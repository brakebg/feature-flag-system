import type { AuditEvent } from '../../api/types';

// Spec 8.6 action labels.
export const ACTION_LABELS: Record<string, string> = {
  GROUP_CREATED: 'Group created',
  GROUP_UPDATED: 'Group updated',
  GROUP_DELETED: 'Group deleted',
  FLAG_CREATED: 'Flag created',
  FLAG_UPDATED: 'Flag updated',
  FLAG_TOGGLED: 'Flag toggled',
  FLAG_DELETED: 'Flag deleted',
};

/** Colour group of an action label (design 5 · Audit log). */
export function actionTone(action: string): 'created' | 'updated' | 'toggled' | 'deleted' {
  if (action.endsWith('_CREATED')) return 'created';
  if (action.endsWith('_TOGGLED')) return 'toggled';
  if (action.endsWith('_DELETED')) return 'deleted';
  return 'updated';
}

/** `<N> flag(s)`: `1 flag`, otherwise `N flags` (spec 8.5). */
export function flagCount(n: number): string {
  return n === 1 ? '1 flag' : `${n} flags`;
}

type Change = { from?: unknown; to?: unknown };

function isChange(value: unknown): value is Change {
  return typeof value === 'object' && value !== null && ('from' in value || 'to' in value);
}

function quoted(value: unknown): string {
  return `“${typeof value === 'string' ? value : ''}”`;
}

/** Spec 8.6 Details text for one audit event. */
export function auditDetails(event: AuditEvent): string {
  const d = (event.details ?? {}) as Record<string, unknown>;
  switch (event.action) {
    case 'FLAG_TOGGLED': {
      const c = d.enabled;
      return isChange(c) ? `${String(c.from)} → ${String(c.to)}` : '';
    }
    case 'FLAG_CREATED':
      return d.enabled === true ? 'Created on' : 'Created off';
    case 'FLAG_DELETED':
      return d.enabled === true ? 'Was on' : 'Was off';
    case 'GROUP_CREATED':
      return `Name: ${typeof d.name === 'string' ? d.name : ''}`;
    case 'GROUP_DELETED': {
      const flags = Array.isArray(d.deletedFlags) ? (d.deletedFlags as unknown[]).map(String) : [];
      return flags.length === 0
        ? '0 flags deleted'
        : `${flagCount(flags.length)} deleted: ${flags.join(', ')}`;
    }
    case 'GROUP_UPDATED':
    case 'FLAG_UPDATED': {
      const parts: string[] = [];
      if (isChange(d.name)) parts.push(`Name: ${quoted(d.name.from)} → ${quoted(d.name.to)}`);
      if (isChange(d.description)) {
        parts.push(`Description: ${quoted(d.description.from)} → ${quoted(d.description.to)}`);
      }
      if (isChange(d.enabled)) {
        parts.push(`Enabled: ${String(d.enabled.from)} → ${String(d.enabled.to)}`);
      }
      return parts.join('; ');
    }
    default:
      return '';
  }
}
