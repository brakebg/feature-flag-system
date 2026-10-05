import type { Flag } from '../../api/types';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { useToast } from '../../components/toastContext';
import { useDeleteFlag } from '../../hooks/queries';
import { errorText } from '../../lib/errors';

/** Spec 8.5 delete-flag confirmation. */
export function DeleteFlagDialog({ flag, onClose }: { flag: Flag; onClose: () => void }) {
  const toast = useToast();
  const remove = useDeleteFlag(flag.groupId);

  async function confirm() {
    try {
      await remove.mutateAsync(flag.id);
      toast.success(`Flag ${flag.fullKey} deleted`);
      onClose();
    } catch (e) {
      toast.error(errorText(e, 'Could not delete flag'));
    }
  }

  return (
    <ConfirmDialog
      title={`Delete flag ${flag.fullKey}?`}
      text="Services reading it will get 404."
      confirmLabel="Delete"
      busy={remove.isPending}
      onConfirm={() => void confirm()}
      onCancel={onClose}
    />
  );
}
