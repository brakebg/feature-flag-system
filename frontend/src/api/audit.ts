import { request } from './apiClient';
import type { AuditPage } from './types';

export const AUDIT_PAGE_SIZE = 50;

/** Spec 6.1 `GET /audit`; spec 8.6 loads 50 per page. */
export const auditApi = {
  page: (page: number, targetKey: string) => {
    const params = new URLSearchParams({ page: String(page), size: String(AUDIT_PAGE_SIZE) });
    if (targetKey) params.set('targetKey', targetKey);
    return request<AuditPage>(`/v1/admin/audit?${params.toString()}`);
  },
};
