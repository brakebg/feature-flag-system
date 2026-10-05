import { zodResolver } from '@hookform/resolvers/zod';
import { useId, useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import type { Flag, GroupDetail } from '../../api/types';
import { Button } from '../../components/Button';
import { Modal } from '../../components/Modal';
import { OptionalLabel } from '../../components/OptionalLabel';
import { TextField } from '../../components/TextField';
import { Toggle } from '../../components/Toggle';
import { useToast } from '../../components/toastContext';
import { useCreateFlag, useUpdateFlag } from '../../hooks/queries';
import { errorText, isVersionConflict, formFieldErrors } from '../../lib/errors';
import {
  flagCreateSchema,
  flagEditSchema,
  type FlagCreateForm,
  type FlagEditForm,
} from '../../schemas/forms';
import styles from './FlagDialog.module.css';

/** Spec 8.4 "New flag in <group name>" dialog (design 3 · New flag dialog). */
export function NewFlagDialog({ group, onClose }: { group: GroupDetail; onClose: () => void }) {
  const toast = useToast();
  const create = useCreateFlag(group.id);
  const stateId = useId();
  const form = useForm<FlagCreateForm>({
    resolver: zodResolver(flagCreateSchema),
    defaultValues: { key: '', description: '', enabled: false },
  });
  const { register, handleSubmit, formState, setError, control } = form;
  const description = useWatch({ control, name: 'description' });
  const enabled = useWatch({ control, name: 'enabled' });

  const submit = handleSubmit(async (values) => {
    try {
      const flag = await create.mutateAsync({
        key: values.key,
        description: values.description === '' ? null : values.description,
        enabled: values.enabled,
      });
      toast.success(`Flag ${flag.fullKey} created`);
      onClose();
    } catch (e) {
      const fields = formFieldErrors(e, ['key', 'description'] as const);
      if (fields.length > 0) {
        for (const [field, message] of fields) setError(field, { message }, { shouldFocus: true });
      } else {
        toast.error(errorText(e, 'Could not create flag'));
      }
    }
  });

  return (
    <Modal
      title={`New flag in ${group.name}`}
      onClose={onClose}
      busy={create.isPending}
      onSubmit={() => void submit()}
      footer={
        <>
          <Button onClick={onClose} disabled={create.isPending}>
            Cancel
          </Button>
          <Button
            type="submit"
            variant="primary"
            busy={create.isPending}
            disabled={create.isPending}
          >
            Create flag
          </Button>
        </>
      }
    >
      <TextField
        label="Key"
        prefix={`${group.key}.`}
        mono
        autoComplete="off"
        {...register('key')}
        error={formState.errors.key?.message}
        help="Lowercase letters, digits and hyphens, 2 to 50 characters. Cannot be changed later."
      />
      <div className={styles.descriptionField}>
        <TextField
          label={
            <>
              Description <span className={styles.optional}>(optional)</span>
            </>
          }
          multiline
          {...register('description')}
          error={formState.errors.description?.message}
        />
        <span className={styles.counter}>{Array.from(description).length} / 500</span>
      </div>
      <div className={styles.initial}>
        <div className={styles.initialText}>
          <span id={stateId} className={styles.initialLabel}>
            Initial state
          </span>
          <span className={styles.initialHelp}>
            {enabled
              ? 'On: services read true as soon as the flag is created.'
              : 'Off: services read false until you turn it on.'}
          </span>
        </div>
        <Controller
          control={control}
          name="enabled"
          render={({ field }) => (
            <div className={styles.switchRow}>
              <Toggle checked={field.value} onChange={field.onChange} labelledBy={stateId} />
              <span className={field.value ? styles.on : styles.off}>
                {field.value ? 'On' : 'Off'}
              </span>
            </div>
          )}
        />
      </div>
    </Modal>
  );
}

/** Spec 8.4 "Edit flag" dialog; sends the version the flag had when it opened (8.5). */
export function EditFlagDialog({ flag, onClose }: { flag: Flag; onClose: () => void }) {
  const toast = useToast();
  const update = useUpdateFlag(flag.groupId);
  const [version] = useState(flag.version);
  const form = useForm<FlagEditForm>({
    resolver: zodResolver(flagEditSchema),
    defaultValues: { description: flag.description ?? '' },
  });
  const { register, handleSubmit, formState, setError } = form;

  const submit = handleSubmit(async (values) => {
    try {
      const updated = await update.mutateAsync({
        id: flag.id,
        body: { description: values.description === '' ? null : values.description, version },
      });
      toast.success(`Flag ${updated.fullKey} updated`);
      onClose();
    } catch (e) {
      if (isVersionConflict(e)) {
        onClose();
        toast.error('This item was changed by someone else');
        return;
      }
      const fields = formFieldErrors(e, ['description'] as const);
      if (fields.length > 0) {
        for (const [field, message] of fields) setError(field, { message }, { shouldFocus: true });
      } else {
        toast.error(errorText(e, 'Could not update flag'));
      }
    }
  });

  return (
    <Modal
      title="Edit flag"
      onClose={onClose}
      busy={update.isPending}
      onSubmit={() => void submit()}
      footer={
        <>
          <Button onClick={onClose} disabled={update.isPending}>
            Cancel
          </Button>
          <Button
            type="submit"
            variant="primary"
            busy={update.isPending}
            disabled={update.isPending}
          >
            Save changes
          </Button>
        </>
      }
    >
      <TextField label="Key" mono readOnly value={flag.fullKey} />
      <TextField
        label={<OptionalLabel text="Description" />}
        multiline
        {...register('description')}
        error={formState.errors.description?.message}
      />
    </Modal>
  );
}
