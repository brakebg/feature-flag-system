import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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

interface ToggleContext {
  detail?: GroupDetail;
  list?: GroupSummary[];
}

/** Spec 8.5: optimistic toggle; on error the switch reverts (the caller shows the toast). */
export function useToggleFlag(groupId: string) {
  const qc = useQueryClient();
  return useMutation<Flag, Error, { flag: Flag; enabled: boolean }, ToggleContext>({
    mutationFn: ({ flag, enabled }) => flagsApi.toggle(flag.id, enabled),
    onMutate: async ({ flag, enabled }) => {
      await qc.cancelQueries({ queryKey: keys.group(groupId) });
      await qc.cancelQueries({ queryKey: keys.groups });
      const detail = qc.getQueryData<GroupDetail>(keys.group(groupId));
      const list = qc.getQueryData<GroupSummary[]>(keys.groups);
      if (detail) {
        qc.setQueryData<GroupDetail>(keys.group(groupId), {
          ...detail,
          flags: detail.flags.map((f) => (f.id === flag.id ? { ...f, enabled } : f)),
        });
      }
      if (list && flag.enabled !== enabled) {
        qc.setQueryData<GroupSummary[]>(
          keys.groups,
          list.map((g) =>
            g.id === groupId ? { ...g, enabledCount: g.enabledCount + (enabled ? 1 : -1) } : g,
          ),
        );
      }
      return { detail, list };
    },
    onError: (_e, _v, context) => {
      if (context?.detail) qc.setQueryData(keys.group(groupId), context.detail);
      if (context?.list) qc.setQueryData(keys.groups, context.list);
    },
    onSuccess: (updated) => {
      qc.setQueryData<GroupDetail>(keys.group(groupId), (old) =>
        old ? { ...old, flags: old.flags.map((f) => (f.id === updated.id ? updated : f)) } : old,
      );
    },
    onSettled: () => qc.invalidateQueries({ queryKey: keys.groups }),
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
