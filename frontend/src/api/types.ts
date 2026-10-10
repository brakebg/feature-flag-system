import type { components } from './schema';

// Spec 6.2 payloads, from the committed OpenAPI contract (gate 8).
type S = components['schemas'];
export type GroupSummary = S['GroupSummary'];
export type Group = S['Group'];
export type GroupDetail = S['GroupDetail'];
export type Flag = S['Flag'];
export type AuditEvent = S['AuditEventView'];
export type AuditPage = S['PagedModelAuditEventView'];
export type CreateGroupRequest = S['CreateGroupRequest'];
export type UpdateGroupRequest = S['UpdateGroupRequest'];
export type CreateFlagRequest = S['CreateFlagRequest'];
export type UpdateFlagRequest = S['UpdateFlagRequest'];
