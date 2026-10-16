# Frontend code structure

From spec 001 (`docs/SPEC.md`) section 8.7. Section numbers are kept, so references
such as "spec 9.1" still point here. Map of all sections: `docs/README.md`.

### 8.7 UI code structure

- `src/api/` — typed fetch client (`apiClient.ts`) that adds the bearer token, parses problem details into an `ApiError` class, and triggers logout on 401; one module per resource (`groups.ts`, `flags.ts`, `audit.ts`, `auth.ts`).
- `src/hooks/` — TanStack Query hooks (`useGroups`, `useGroup`, `useCreateFlag`, `useToggleFlag`, ...). Query keys: `['groups']`, `['group', id]`, `['audit', filters]`.
- `src/components/` — `Modal`, `ConfirmDialog`, `Toggle`, `Toast`, `Button`, `TextField`, `EmptyState`.
- `src/features/` — `auth/`, `groups/`, `flags/`, `audit/` screens.
- `src/schemas/` — Zod schemas shared by forms.
- API base URL from `import.meta.env.VITE_API_BASE_URL` (default `/api`); in dev Vite proxies `/api` to `http://localhost:8080`.

Accessibility: every control has a label, the toggle is a `button role="switch"` with `aria-checked`, modals trap focus and close on Escape.

- Controls that perform an action are `button` elements. Only the navigation entries `Flags` and `Audit log` (and the link on the 404 page) are links.
- The flags table and the audit table expose table semantics (`table`, `row`, `columnheader`, `cell`). Column headers: flags `Key`, `Description`, `Status`, `Created by`, `Updated`, `Actions`; audit `Time`, `Actor`, `Action`, `Target`, `Details`.
- Accessible names: flag switch `Toggle <fullKey>` (`role="switch"`, `aria-checked`); row buttons `Edit <fullKey>` and `Delete <fullKey>`; group header button `Copy group key`; inputs labelled `Search groups`, `Search flags`, `Filter by target key`; the status filter is a group named `Filter by status` with buttons `All`, `On`, `Off` using `aria-pressed`; the group list is a `nav` named `Flag groups`; the delete-group input is labelled `Type <groupKey> to confirm`; the group buttons in the list have the group name and key as text.
