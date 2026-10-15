# Acceptance criteria — spec 005

Owner-authored, locked. IDs for gate 14 (traceability).

Due milestone: 12

Colours are computed CSS values (`rgb(...)`), as a browser test reads them. "The screens" means:
`/login?expired=1`, `/groups/<id>` with flags, `/groups` with no groups, a group with no flags,
`/audit` with events, the 404 page, and each dialog open (New group, Edit group, New flag,
Edit flag, Delete flag, Delete group). "Long data" means: a group name of 100 characters without
spaces, a group description of 500 characters, a flag key of 50 characters without hyphens (full
key 101 characters), and a flag description of 500 characters.

- [ ] **AC-UI-1** · On the screens, the `body` background is `rgb(241, 242, 244)` and its text colour `rgb(28, 36, 51)`; the `body` font family starts with `"IBM Plex Sans"`; every `h1` uses a font family that starts with `"IBM Plex Sans Condensed"` with weight 600; flag keys and full keys use a font family that starts with `"IBM Plex Mono"`; after `document.fonts.ready`, `document.fonts.check` is true for `600 56px "IBM Plex Sans Condensed"`, `400 14px "IBM Plex Sans"` and `500 15px "IBM Plex Mono"`; no request goes to an origin other than the UI origin.
- [ ] **AC-UI-2** · The buttons `New flag`, `Create flag`, `Create group`, `Save changes`, `Add flag` and `Create your first group`, and every switch with `aria-checked="true"`, have the background `rgb(61, 89, 168)`; a switch with `aria-checked="false"` has `rgb(205, 210, 219)`; `Sign in` and `Load more` have `rgb(28, 36, 51)`; an enabled `Delete` or `Delete group and <N> flag(s)` button has `rgb(181, 71, 58)`.
- [ ] **AC-UI-3** · On the screens, every visible `button` element except the group buttons in the `Flag groups` list, and the inputs `Search groups`, `Search flags` and `Filter by target key`, have a computed border radius of at least half their rendered height, or `50%` on a square element.
- [ ] **AC-UI-4** · Each group button in the `Flag groups` list contains one element with `aria-hidden="true"` whose trimmed `getPropertyValue('--share')` equals `floor((200 × enabled + total) / (2 × total))` followed by `%`, and `0%` when the group has no flags (for example 1 of 8 gives `13%`, 23 of 40 gives `58%`, 2 of 3 gives `67%`); after a toggle the value follows the new badge text.
- [ ] **AC-UI-5** · Each Action label in the audit table keeps its spec 001 text and starts with one element with `aria-hidden="true"`; the label background and text colour are: `Group created` and `Flag created` `rgb(223, 229, 243)` / `rgb(45, 68, 134)`; `Group updated` and `Flag updated` `rgb(236, 238, 241)` / `rgb(61, 69, 86)`; `Flag toggled` `rgb(248, 230, 218)` / `rgb(138, 75, 38)`; `Group deleted` and `Flag deleted` `rgb(245, 224, 220)` / `rgb(154, 58, 47)`.
- [ ] **AC-UI-6** · On the screens, no element with `aria-hidden="true"` is focusable, has a `tabindex` or contains a focusable element; and the ARIA snapshot (roles, names, `aria-checked`, `aria-pressed`, `aria-current`, `aria-invalid`) of each screen equals the snapshot recorded from the code before the redesign.
- [ ] **AC-UI-7** · At viewport widths 768, 800, 1024 and 1440 px, on the screens: `document.documentElement.scrollWidth` is not larger than the viewport width; the flags table and the audit table containers have `scrollWidth` ≤ `clientWidth`; every `Edit <fullKey>` and `Delete <fullKey>` button lies inside the viewport; at 1024 px and below the `Flag groups` panel is above the flags table; below 860 px the sign-in art panel is not displayed and the column rules of spec 001 sections 8.4 and 8.6 hold.
- [ ] **AC-UI-8** · With long data, at 1440 and 800 px: every element that shows a group name, group key, description, flag key or full key has `scrollWidth` ≤ `clientWidth` (it wraps, nothing is clipped), and in the group header and audit header cards no box of an `h1` or `p` intersects the box of an element with `aria-hidden="true"`; at 1280 × 600 with a group of 30 flags, after typing the group key, `Delete group and 30 flags` can be scrolled into view inside the dialog and clicked.
- [ ] **AC-UI-9** · Moving focus with Tab through each screen, every focused element shows a solid 2 px outline with a 2 px offset, `rgb(61, 89, 168)` on light surfaces and `rgb(255, 255, 255)` inside the audit header card.
