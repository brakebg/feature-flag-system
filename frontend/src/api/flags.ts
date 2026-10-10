import { request } from './apiClient';
import type { CreateFlagRequest, Flag, UpdateFlagRequest } from './types';

/** Spec 6.1 flag endpoints. */
export const flagsApi = {
  create: (groupId: string, body: CreateFlagRequest) =>
    request<Flag>(`/v1/admin/groups/${encodeURIComponent(groupId)}/flags`, {
      method: 'POST',
      body,
    }),
  update: (id: string, body: UpdateFlagRequest) =>
    request<Flag>(`/v1/admin/flags/${encodeURIComponent(id)}`, { method: 'PATCH', body }),
  toggle: (id: string, enabled: boolean) =>
    request<Flag>(`/v1/admin/flags/${encodeURIComponent(id)}/toggle`, {
      method: 'POST',
      body: { enabled },
    }),
  remove: (id: string) =>
    request<undefined>(`/v1/admin/flags/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};
