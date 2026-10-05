import { request } from './apiClient';
import type {
  CreateGroupRequest,
  Group,
  GroupDetail,
  GroupSummary,
  UpdateGroupRequest,
} from './types';

/** Spec 6.1 group endpoints. */
export const groupsApi = {
  list: () => request<GroupSummary[]>('/v1/admin/groups'),
  get: (id: string) => request<GroupDetail>(`/v1/admin/groups/${encodeURIComponent(id)}`),
  create: (body: CreateGroupRequest) =>
    request<Group>('/v1/admin/groups', { method: 'POST', body }),
  update: (id: string, body: UpdateGroupRequest) =>
    request<Group>(`/v1/admin/groups/${encodeURIComponent(id)}`, { method: 'PATCH', body }),
  remove: (id: string) =>
    request<undefined>(`/v1/admin/groups/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};
