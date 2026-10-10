import { useId, useState } from 'react';
import type { GroupDetail } from '../../api/types';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { useToast } from '../../components/toastContext';
import { useDeleteGroup } from '../../hooks/queries';
import { errorText, isNotFound } from '../../lib/errors';
import { flagCount } from '../audit/auditFormat';
import styles from './DeleteGroupDialog.module.css';

/**
 * Spec 8.5 delete-group confirmation (design 4): lists the flags and needs the exact group key
 * typed before the delete button is enabled.
 */
export function DeleteGroupDialog({
  group,
  onClose,
  onDeleted,
}: {
  group: GroupDetail;
  onClose: () => void;
  onDeleted: () => void;
}) {
  const toast = useToast();
  const remove = useDeleteGroup();
  const [typed, setTyped] = useState('');
  const inputId = useId();
  const n = group.flags.length;

  async function confirm() {
    try {
      await remove.mutateAsync(group.id);
      toast.success(`Group ${group.key} and ${flagCount(n)} deleted`);
      onDeleted();
    } catch (e) {
      if (isNotFound(e)) {
        toast.error(`Group ${group.key} was already deleted`);
        onDeleted();
        return;
      }
      toast.error(errorText(e, 'Could not delete group'));
    }
  }

  return (
    <ConfirmDialog
      title={`Delete group “${group.name}”?`}
      text={`This permanently deletes the group and all ${flagCount(n)} in it. Services reading these flags will get 404.`}
      confirmLabel={`Delete group and ${flagCount(n)}`}
      confirmDisabled={typed !== group.key}
      busy={remove.isPending}
      onConfirm={() => void confirm()}
      onCancel={onClose}
    >
      {n > 0 && (
        <ul aria-label="Flags that will be deleted" className={styles.list}>
          {group.flags.map((f) => (
            <li key={f.id}>{f.fullKey}</li>
          ))}
        </ul>
      )}
      <div className={styles.field}>
        <label htmlFor={inputId} className={styles.label}>
          Type <span className={styles.key}>{group.key}</span> to confirm
        </label>
        <input
          id={inputId}
          type="text"
          autoComplete="off"
          spellCheck={false}
          placeholder={group.key}
          className={styles.input}
          value={typed}
          onChange={(e) => setTyped(e.target.value)}
        />
      </div>
    </ConfirmDialog>
  );
}
