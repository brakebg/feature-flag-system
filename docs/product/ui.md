# Admin UI behaviour

From spec 001 (`docs/SPEC.md`) section 8, 8.1–8.6. Section numbers are kept, so references
such as "spec 9.1" still point here. Map of all sections: `docs/README.md`.

## 8. Frontend UI

The UI is a three-route SPA: a login page, a two-pane flags workspace (groups on the left, the selected group's flags on the right), and an audit log. Simple, clean, keyboard accessible, desktop-first but usable down to 768 px wide.

**Visual reference.** The approved screen designs are committed in `docs/product/design/`, one HTML file and one PNG per screen: sign in, flags workspace, new flag dialog, delete group confirmation, audit log, and `6 · States`, a reference sheet of empty states, toasts, errors, busy and focus states, Edit flag, Delete flag and the 404 page (spec 005, "Redesign C · balanced colours"). The UI MUST match them in layout, colours, typography (IBM Plex Sans, IBM Plex Sans Condensed 600 for page and dialog titles, IBM Plex Mono), spacing, shapes and component states. Every shape is decorative (`aria-hidden="true"`, never focusable) and changes no behaviour. Where a design and this section disagree, this section decides behaviour and the design decides appearance. Roles, accessible names and ARIA states are behaviour: where the design markup differs from 8.7, 8.7 decides. Names and values shown in the designs are sample data only.

### 8.1 Routes

| Route | Screen | Auth |
| --- | --- | --- |
| `/login` | Login form | Public; logged-in users are redirected to `/` |
| `/` | Redirects to `/groups` | Required |
| `/groups` and `/groups/:groupId` | Flags workspace; the URL holds the selected group so it survives refresh | Required |
| `/audit` | Audit log | Required |
| `*` | 404 page with a link back | — |

A `RequireAuth` wrapper checks the token (decode `exp` client-side). It redirects to `/login` when there is no token, and to `/login?expired=1` (clearing the token) when the token is present but its `exp` is in the past.

The 404 page has the heading `Page not found` and a link `Back to flags` that goes to `/groups`.

### 8.2 Login page

- Form next to a decorative art panel (design 1; the panel is hidden below 860 px): app title "Feature Flags", Username, Password, "Sign in" button.
- Button disabled and shows a spinner while the request runs; Enter submits.
- Errors: 401 → "Invalid username or password"; 429 (returned by the edge, section 5.3; N from its `Retry-After` header) → "Too many attempts, try again in N seconds"; network → "Cannot reach server". `N seconds` is `1 second` when N is 1. If `Retry-After` is missing or not a whole number of seconds, the text is `Too many attempts, try again later`.
- `?expired=1` shows an info banner "Your session expired. Please sign in again." (`role="status"`).
- The error message is shown in an element with `role="alert"` between the fields and the Sign in button. The text `Sessions last 8 hours.` in the design is replaced by no text (the lifetime is configurable).

### 8.3 App shell

Top bar (a floating white pill, designs 2 and 5): app name, nav links (Flags, Audit log), signed-in username, "Sign out" button. Content area below.

`Sign out` removes the token and goes to `/login` (no banner). Going Back afterwards shows the login page again; no Admin API request is sent without a token.

### 8.4 Flags workspace

Left pane (groups, \~280 px):

- Search box filtering groups (client-side): a group is shown when the text is a case-insensitive substring of its key or name; empty text shows all groups.
- List items show group name, key in monospace, and a badge `enabled/total` (e.g. `1/2`).
- `New group` button opens a modal: Key, Name, Description. Key field auto-suggests a slug from Name until the user edits Key manually. Slug rule: lower-case the name, replace every run of characters outside `a-z` and `0-9` with one `-`, remove leading and trailing `-`, cut to 50 characters. Example: `My New Group` gives `my-new-group`. The rule applies to the New group dialog only.
- After a group is created it is selected and the URL becomes `/groups/<id>`. A `/groups/<id>` URL with an unknown id shows the placeholder `Select a group or create one`.
- Empty state: "No groups yet" + `Create your first group` button.
- A group search with no result shows `No groups match your search`.

Right pane (selected group):

- Header: group name, key (with copy button), description, a meta line "Created by admin · Updated by admin, 2 hours ago" (from createdBy, updatedBy, updatedAt), `Edit group` (modal for name/description) and `Delete group` (danger) buttons. The group header description line ends with `<n> of <m> flags on`.
- Flags table, columns: Key (key in monospace, the full key `<groupKey>.<flagKey>` as visible text under it), Description, Status toggle switch, Created by (username), Updated (relative time, with `by <updatedBy>` underneath), Actions (Edit, Delete). Next to the switch the text `On` or `Off` is shown. Below 860 px only `Created by` and `Updated` are hidden in the flags table; `Description` and `Actions` stay visible.
- Search box filters flags in the group (client-side): a flag is shown when the text is a case-insensitive substring of its key or description, and it also matches the status filter. A filter chip set: All / On / Off.
- `New flag` button opens a modal: Key, Description, Initial state (switch, default off).
- The plus sign in `New group` and `New flag` is a decorative icon with `aria-hidden="true"`; it is not part of the button name.
- Empty group state: "No flags in this group" + `Add flag` button.
- A flag search or status filter with no result shows `No flags match this filter.`
- No group selected: placeholder "Select a group or create one".
- The wording of relative times is not specified.

Dialog texts (the New flag dialog matches its design; the others follow the same style):

| Dialog | Title (heading, names the dialog) | Fields (label) | Buttons |
| --- | --- | --- | --- |
| New group | `New group` | `Key`, `Name`, `Description (optional)` | `Cancel`, `Create group` |
| Edit group | `Edit group` | `Key` (read-only, shows the key), `Name`, `Description (optional)` | `Cancel`, `Save changes` |
| New flag | `New flag in <group name>` | `Key` (prefix `<groupKey>.` shown), `Description (optional)`, switch `Initial state` | `Cancel`, `Create flag` |
| Edit flag | `Edit flag` | `Key` (read-only, shows the full key), `Description (optional)` | `Cancel`, `Save changes` |

Every dialog in this table has `role="dialog"` and `aria-modal="true"` and is named by its title. Each also has a close icon button named `Close`. The read-only `Key` field in Edit group and Edit flag is an `<input readonly>` labelled `Key`; its value is the group key or the full flag key. The confirm dialogs are defined in 8.5.

### 8.5 Interaction rules

| Action | Behavior |
| --- | --- |
| Toggle a flag | Optimistic update via TanStack Query; calls `POST /flags/{id}/toggle` with the new value; on error revert and show a toast. Switch is disabled while its request is in flight. |
| Delete a flag | Confirm dialog (texts below). |
| Delete a group | Confirm dialog (texts below) lists the flag count and requires typing the group key to enable the delete button. After success, navigate to `/groups` and show the success toast below. |
| Create / edit | Inline field errors from Zod; server 409 shown on the Key field ("Key already exists"); server 400 field errors mapped onto fields (the server `message` of that field is shown). |
| Concurrent edit (409 version conflict) | The Edit group and Edit flag dialogs send the `version` the item had when the dialog was opened, even if newer data arrives while the dialog is open. On 409: the dialog closes, an error toast "This item was changed by someone else" appears (`role="alert"`), and the group is refetched, so the header and the flags table show the current server values. |
| Any success | Toast for 3 s (create, update, delete). Toggles do not toast. |

Toasts are rendered with `role="status"` (success) or `role="alert"` (error). Toggle failure toast text: `Could not update flag <fullKey>` (full key without quotes). Success toast texts: `Group <key> created`, `Group <key> updated`, `Flag <fullKey> created`, `Flag <fullKey> updated`, `Flag <fullKey> deleted`, and after a group delete `Group <key> and <N> flag(s) deleted`.

Confirm dialogs use `role="alertdialog"` and are named by their title:

- Delete flag dialog: title `Delete flag <fullKey>?`, text `Services reading it will get 404.`, buttons `Cancel` and `Delete`.
- Delete group dialog: title `Delete group “<name>”?`, text `This permanently deletes the group and all <N> flag(s) in it. Services reading these flags will get 404.`, a list named `Flags that will be deleted` with the full keys, a text field labelled `Type <groupKey> to confirm`, buttons `Cancel` and `Delete group and <N> flag(s)`. The delete button is disabled until the field equals the key exactly (case-sensitive, no trimming).
- `<N> flag(s)` is `1 flag` when N is 1, otherwise `N flags`. No backticks appear in the UI.

Validation in dialogs: a field that fails validation gets `aria-invalid="true"` and a visible error text linked with `aria-describedby`; the dialog stays open and no request is sent. Validation runs when the user clicks the submit button (`Create group`, `Create flag`, `Save changes`) or presses Enter; it may also run on blur. The submit button is enabled except while its request is in flight. Inputs never change what the user typed (no automatic lower-casing or trimming of the Key field); only the New group slug suggestion fills Key while the user has not edited it. Server field errors (409 on Key, 400 field errors) are shown the same way: the field gets `aria-invalid="true"`, the text is linked with `aria-describedby`, and the dialog stays open. Texts: key `Use 2 to 50 lowercase letters, digits or hyphens, starting with a letter`; name `Name is required` / `Name must be at most 100 characters`; description `Description must be at most 500 characters`.

### 8.6 Audit log page

Table: Time (local, absolute + relative), Actor, Action (coloured label that starts with a decorative shape: circle created, ring updated, half circle toggled, square deleted), Target, Details. Filter input on target key, "Load more" pagination (50 per page).

- The target filter applies while the user types and is sent as `targetKey`; the results replace the list. `Load more` is shown only while `page.number + 1 < page.totalPages` and appends the next 50 events.
- Below 860 px the `Time` and `Actor` columns are hidden; `Action`, `Target` and `Details` stay visible.
- Action labels: GROUP\_CREATED `Group created`, GROUP\_UPDATED `Group updated`, GROUP\_DELETED `Group deleted`, FLAG\_CREATED `Flag created`, FLAG\_UPDATED `Flag updated`, FLAG\_TOGGLED `Flag toggled`, FLAG\_DELETED `Flag deleted`.
- Details text: FLAG\_TOGGLED `false → true` or `true → false`; FLAG\_CREATED `Created off` or `Created on`; FLAG\_DELETED `Was on` or `Was off`; GROUP\_CREATED `Name: <name>`; GROUP\_UPDATED `Name: “<from>” → “<to>”` and/or `Description: “<from>” → “<to>”`; FLAG\_UPDATED `Description: “<from>” → “<to>”` and/or `Enabled: false → true`; GROUP\_DELETED `<N> flag(s) deleted: <full keys, comma separated>`.
- When several fields changed, the parts appear in the order Name, Description, Enabled, joined by `; ` (for example `Description: “a” → “b”; Enabled: false → true`). A description that does not exist shows as `“”`. GROUP\_DELETED with no flags shows `0 flags deleted`. Full keys are joined by `, ` (comma and space).
- The format of the Time column is not specified.
