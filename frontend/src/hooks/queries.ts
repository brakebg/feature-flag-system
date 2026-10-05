import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useToast } from '../components/toastContext';
import { auditApi } from '../api/audit';
import { flagsApi } from '../api/flags';
import { groupsApi } from '../api/groups';
import type {
  CreateFlagRequest,
  CreateGroupRequest,
  Flag,
  GroupDetail,
  GroupSummary,
  UpdateFlagRequest,
  UpdateGroupRequest,
} from '../api/types';

// Spec 8.7 query keys.
export const keys = {
  groups: ['groups'] as const,
  group: (id: string) => ['group', id] as const,
  audit: (filters: { targetKey: string }) => ['audit', filters] as const,
};

export function useGroups() {
  return useQuery({ queryKey: keys.groups, queryFn: groupsApi.list });
}

export function useGroup(id: string | undefined) {
  return useQuery({
    queryKey: keys.group(id ?? ''),
    queryFn: () => groupsApi.get(id as string),
    enabled: !!id,
  });
}

export function useCreateGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateGroupRequest) => groupsApi.create(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.groups }),
  });
}

export function useUpdateGroup(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateGroupRequest) => groupsApi.update(id, body),
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: keys.groups });
      return qc.invalidateQueries({ queryKey: keys.group(id) });
    },
  });
}

export function useDeleteGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => groupsApi.remove(id),
    onSuccess: (_v, id) => {
      qc.removeQueries({ queryKey: keys.group(id) });
      return qc.invalidateQueries({ queryKey: keys.groups });
    },
  });
}

export function useCreateFlag(groupId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateFlagRequest) => flagsApi.create(groupId, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: keys.groups });
      return qc.invalidateQueries({ queryKey: keys.group(groupId) });
    },
  });
}

export function useUpdateFlag(groupId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateFlagRequest }) =>
      flagsApi.update(id, body),
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: keys.groups });
      return qc.invalidateQueries({ queryKey: keys.group(groupId) });
    },
  });
}

export function useDeleteFlag(groupId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => flagsApi.remove(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: keys.groups });
      return qc.invalidateQueries({ queryKey: keys.group(groupId) });
    },
  });
}

export const toggleKey = (flagId: string) => ['toggle', flagId] as const;

/**
 * Spec 8.5: optimistic toggle of one flag. On error only this flag is set back (a newer change of
 * another flag stays) and the toast shows. Both run at mutation level, so they also run when the
 * row is gone before the request ends. The group is refetched after every toggle.
 */
export function useToggleFlag(groupId: string, flag: Flag) {
  const qc = useQueryClient();
  const toast = useToast();
  const setEnabled = (enabled: boolean) => {
    let changed = false;
    qc.setQueryData<GroupDetail>(keys.group(groupId), (old) =>
      old
        ? {
            ...old,
            flags: old.flags.map((f) => {
              if (f.id !== flag.id) return f;
              changed = f.enabled !== enabled;
              return { ...f, enabled };
            }),
          }
        : old,
    );
    if (changed) {
      qc.setQueryData<GroupSummary[]>(keys.groups, (list) =>
        list?.map((g) =>
          g.id === groupId ? { ...g, enabledCount: g.enabledCount + (enabled ? 1 : -1) } : g,
        ),
      );
    }
  };
  return useMutation<Flag, Error, boolean>({
    mutationKey: toggleKey(flag.id),
    mutationFn: (enabled) => flagsApi.toggle(flag.id, enabled),
    onMutate: async (enabled) => {
      await qc.cancelQueries({ queryKey: keys.group(groupId) });
      await qc.cancelQueries({ queryKey: keys.groups });
      setEnabled(enabled);
    },
    onError: (_e, enabled) => {
      setEnabled(!enabled);
      toast.error(`Could not update flag ${flag.fullKey}`);
    },
    onSuccess: (updated) => {
      qc.setQueryData<GroupDetail>(keys.group(groupId), (old) =>
        old ? { ...old, flags: old.flags.map((f) => (f.id === updated.id ? updated : f)) } : old,
      );
    },
    onSettled: () =>
      Promise.all([
        qc.invalidateQueries({ queryKey: keys.group(groupId) }),
        qc.invalidateQueries({ queryKey: keys.groups }),
      ]),
  });
}

/** Spec 8.6: audit pages of 50, appended with Load more; a new filter starts again. */
export function useAudit(targetKey: string) {
  return useInfiniteQuery({
    queryKey: keys.audit({ targetKey }),
    queryFn: ({ pageParam }) => auditApi.page(pageParam, targetKey),
    initialPageParam: 0,
    getNextPageParam: (last) =>
      last.page.number + 1 < last.page.totalPages ? last.page.number + 1 : undefined,
  });
}
