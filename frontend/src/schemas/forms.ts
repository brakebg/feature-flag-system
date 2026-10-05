import { z } from 'zod';

// Spec 10.2: the CSP has no 'unsafe-eval'. Without this, Zod probes `new Function` once, and
// browsers report that as a CSP violation (gate 12) even though Zod catches the error.
z.config({ jitless: true });

// Spec 4.2 validation rules, the same as the backend's Bean Validation (8.5 texts).
export const KEY_MESSAGE =
  'Use 2 to 50 lowercase letters, digits or hyphens, starting with a letter';
const KEY = /^[a-z][a-z0-9-]{1,49}$/;

/** Lengths count Unicode code points (spec 4.2 item 2). */
const codePoints = (s: string) => Array.from(s).length;

const key = z.string().regex(KEY, KEY_MESSAGE);
const name = z
  .string()
  .refine((s) => s.trim().length > 0, 'Name is required')
  .refine((s) => codePoints(s.trim()) <= 100, 'Name must be at most 100 characters');
const description = z
  .string()
  .refine((s) => codePoints(s) <= 500, 'Description must be at most 500 characters');

export const groupCreateSchema = z.object({ key, name, description });
export const groupEditSchema = z.object({ name, description });
export const flagCreateSchema = z.object({ key, description, enabled: z.boolean() });
export const flagEditSchema = z.object({ description });

export type GroupCreateForm = z.infer<typeof groupCreateSchema>;
export type GroupEditForm = z.infer<typeof groupEditSchema>;
export type FlagCreateForm = z.infer<typeof flagCreateSchema>;
export type FlagEditForm = z.infer<typeof flagEditSchema>;

/** Spec 8.4: slug suggestion for the New group key. */
export function slugify(name: string): string {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 50);
}
