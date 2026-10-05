import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import type { GroupDetail } from '../../api/types';
import { Button } from '../../components/Button';
import { Modal } from '../../components/Modal';
import { TextField } from '../../components/TextField';
import { useToast } from '../../components/toastContext';
import { useCreateGroup, useUpdateGroup } from '../../hooks/queries';
import { errorText, isVersionConflict, formFieldErrors } from '../../lib/errors';
import {
  groupCreateSchema,
  groupEditSchema,
  slugify,
  type GroupCreateForm,
  type GroupEditForm,
} from '../../schemas/forms';

/** Spec 8.4 "New group" dialog; Key follows a slug of Name until the user edits Key. */
export function NewGroupDialog({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (id: string) => void;
}) {
  const toast = useToast();
  const create = useCreateGroup();
  const [keyTouched, setKeyTouched] = useState(false);
  const form = useForm<GroupCreateForm>({
    resolver: zodResolver(groupCreateSchema),
    defaultValues: { key: '', name: '', description: '' },
  });
  const { register, handleSubmit, formState, setError, setValue } = form;

  const submit = handleSubmit(async (values) => {
    try {
      const group = await create.mutateAsync({
        key: values.key,
        name: values.name,
        description: values.description === '' ? null : values.description,
      });
      toast.success(`Group ${group.key} created`);
      onCreated(group.id);
    } catch (e) {
      const fields = formFieldErrors(e, ['key', 'name', 'description'] as const);
      if (fields.length > 0) {
        for (const [field, message] of fields) setError(field, { message }, { shouldFocus: true });
      } else {
        toast.error(errorText(e, 'Could not create group'));
      }
    }
  });

  const name = register('name', {
    onChange: (event: { target: { value: string } }) => {
      if (!keyTouched) {
        setValue('key', slugify(event.target.value), {
          shouldValidate: formState.isSubmitted,
        });
      }
    },
  });
  const key = register('key', { onChange: () => setKeyTouched(true) });

  return (
    <Modal
      title="New group"
      onClose={onClose}
      onSubmit={() => void submit()}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button
            type="submit"
            variant="primary"
            busy={create.isPending}
            disabled={create.isPending}
          >
            Create group
          </Button>
        </>
      }
    >
      <TextField
        label="Key"
        mono
        autoComplete="off"
        {...key}
        error={formState.errors.key?.message}
      />
      <TextField label="Name" autoComplete="off" {...name} error={formState.errors.name?.message} />
      <TextField
        label="Description (optional)"
        multiline
        {...register('description')}
        error={formState.errors.description?.message}
      />
    </Modal>
  );
}

/** Spec 8.4 "Edit group" dialog; sends the version the group had when the dialog opened (8.5). */
export function EditGroupDialog({ group, onClose }: { group: GroupDetail; onClose: () => void }) {
  const toast = useToast();
  const update = useUpdateGroup(group.id);
  const [version] = useState(group.version);
  const form = useForm<GroupEditForm>({
    resolver: zodResolver(groupEditSchema),
    defaultValues: { name: group.name, description: group.description ?? '' },
  });
  const { register, handleSubmit, formState, setError } = form;

  const submit = handleSubmit(async (values) => {
    try {
      const updated = await update.mutateAsync({
        name: values.name,
        description: values.description === '' ? null : values.description,
        version,
      });
      toast.success(`Group ${updated.key} updated`);
      onClose();
    } catch (e) {
      if (isVersionConflict(e)) {
        onClose();
        toast.error('This item was changed by someone else');
        return;
      }
      const fields = formFieldErrors(e, ['name', 'description'] as const);
      if (fields.length > 0) {
        for (const [field, message] of fields) setError(field, { message }, { shouldFocus: true });
      } else {
        toast.error(errorText(e, 'Could not update group'));
      }
    }
  });

  return (
    <Modal
      title="Edit group"
      onClose={onClose}
      onSubmit={() => void submit()}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
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
      <TextField label="Key" mono readOnly value={group.key} />
      <TextField
        label="Name"
        autoComplete="off"
        {...register('name')}
        error={formState.errors.name?.message}
      />
      <TextField
        label="Description (optional)"
        multiline
        {...register('description')}
        error={formState.errors.description?.message}
      />
    </Modal>
  );
}
