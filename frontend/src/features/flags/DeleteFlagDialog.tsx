import type { Flag } from '../../api/types';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { useToast } from '../../components/toastContext';
import { useDeleteFlag } from '../../hooks/queries';
import { errorText, isNotFound } from '../../lib/errors';

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
      if (isNotFound(e)) {
        // FF-4: someone else deleted it; the row goes away with the refreshed group.
        toast.error(`Flag ${flag.fullKey} was already deleted`);
        onClose();
        return;
      }
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
