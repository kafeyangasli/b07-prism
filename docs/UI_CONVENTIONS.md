# PRISM UI Conventions

Use these rules for new Thymeleaf views so feature work remains visually and behaviorally consistent.

## Layout and type

- Start pages with `fragments/layout :: layout(pageTitle, activeNav, content)`; do not duplicate the document shell or navigation.
- Use `.ui-container` (`max-w-7xl`) for page content. Default page spacing is `py-8 sm:py-10` from the shared layout.
- Use the shared page heading fragment or `.ui-page-header`. Page titles inherit responsive sizing and semibold weight; section titles inherit `text-xl font-semibold`. Supporting copy uses `.ui-description`, `.ui-hint`, or `text-muted`.
- Use the existing violet/ink tokens: `bg-page`, `bg-surface`, `border-border`, and `.ui-card`. Danger treatments use `text-danger` and `border-danger-border`. Avoid gradients and decorative motion.
- Use the Tailwind spacing scale: 4/6 for page gutters, 5/6 for card padding, 5 for form gaps, and 6/8 for page sections on mobile/desktop. Keep grid children shrinkable with `min-w-0`.
- Use rounded-2xl surfaces, rounded-xl controls, and rounded-full status badges. Keep violet/lavender accents and the existing system sans typography; the brand SVGs remain authoritative.
- Compose pages around their task: facility gallery plus facts, reservation sections plus guidance, report information plus evidence, and profile plus security. Avoid stretching short forms across the workspace.

## Shared controls

- Buttons always use `.ui-btn` plus one variant: `.ui-btn-primary`, `.ui-btn-secondary`, `.ui-btn-ghost`, or `.ui-btn-danger`.
- Destructive actions use `.ui-btn-danger`, a specific verb, and confirmation when data cannot be restored.
- Fields use `.ui-label` and `.ui-input`; help text uses `.ui-hint`. Add `.ui-input-error`, `aria-invalid="true"`, and a nearby `.ui-field-error` when invalid.
- Place `fragments/feedback :: formErrors` inside a `th:object` form for the validation summary.

## Surfaces, tables, and empty states

- Cards use `.ui-card` with an inner `.ui-card-body`.
- Wrap tables in `.ui-table-wrap` and apply `.ui-table` to the table. Keep action columns rightmost with `.ui-table-actions`. Every data cell needs `data-label` because ordinary tables become labelled records below 1024px.
- Comparison tables use `.ui-table-scroll` to retain columns, a focusable labelled region, and a scroll hint. Use `.ui-table-number` on numeric headers and cells for right alignment and tabular figures.
- Keep labels, inputs, and help text together in a field wrapper. Connect help/error text with `aria-describedby`; reservation fields also expose server validation with `aria-invalid`.
- Empty states use `.ui-empty-state`, a short title, one helpful sentence, and at most one next action.
- `fragments/workspace` provides decorative SVG icons, numbered form headings, account tabs, search tools, history status filters, and the shared authentication aside. Icons supplement visible labels.
- Search tools use `data-list-search` with a stable region ID and `data-list-record` on each item. Use `data-search-text` on cards when text must include facility code/location. History records expose `data-record-status`. These controls are hidden until JavaScript initializes; server filters and records remain usable without it.
- Character counts use `data-character-count` on a textarea with `maxlength`. Password fields may use `data-password-toggle`; generated controls never change field names or values.
- Full-page and dialog report submissions share `reports/form :: report-fields(modal)`. The full-page form is the normal-link fallback; the dialog retains its HTMX target and upload encoding.
- Pagination should preserve active filters, use normal links by default, and expose the current page with `aria-current="page"`.

## Status badges

Render enums with `fragments/status :: badge(status)`. The badge always includes text; color is supplementary.

| Meaning | Values | Treatment |
| --- | --- | --- |
| Awaiting work | `PENDING`, `NEW`, `SCHEDULED` | Amber |
| Usable / approved | `APPROVED`, `ACTIVE` | Violet |
| In progress | `IN_PROGRESS` | Blue |
| Finished successfully | `COMPLETED`, `RESOLVED` | Emerald |
| Rejected | `REJECTED` | Red |
| No longer active | `CANCELLED`, `EXPIRED`, `INACTIVE` | Slate |

## Feedback and dialogs

- Render redirect messages with `fragments/feedback :: flash`. Controllers may use existing `success` / `error` or `successMessage` / `errorMessage` keys.
- Use native `<dialog>` for a true modal. Give it a visible title, labelled close button, cancel action, focusable first control, and a non-JavaScript path to the same operation.
- For an HTMX-loaded dialog, target the dialog's content container and open it only after a successful swap. Do not put destructive confirmation exclusively in client-side JavaScript.

## HTMX contract

- Use HTMX only where a partial update reduces friction; every interaction must still work as a normal link or form submission.
- Give every replacement region a stable ID. Use explicit `hx-target`; default to `hx-swap="outerHTML"` for self-contained regions and `innerHTML` only when the wrapper owns persistent state.
- Use `hx-push-url="true"` for filter/search state users may bookmark. Do not push URLs for mutations or modal loads.
- Full-page controllers may remain unchanged when `hx-select` can safely extract a stable region, as on the facility catalogue.
- Use `hx-indicator="#global-progress"` for requests. Add local indicator copy when the affected region benefits from it.
- Return the same form/fragment with field errors for validation failures. Use HTTP `422` only when the response is deliberately configured for swapping; otherwise return `200` with errors.
- Unexpected `4xx/5xx` and network failures use the shared `#htmx-error` alert. Do not replace useful page content with a generic server error.

## CSRF

- Keep Spring Security CSRF enabled.
- Normal Thymeleaf `POST`, `PUT`, `PATCH`, and `DELETE` forms receive the hidden CSRF field automatically.
- The shared layout exposes `_csrf` and `_csrf_header` meta values. `static/js/app.js` attaches that token to every HTMX request through `htmx:configRequest`.
- Never hard-code a token, disable CSRF for an HTML endpoint, or send a state-changing request with `GET`.

## Navigation

- `fragments/navigation` is authoritative. Visibility uses Spring Security dialect checks, while `SecurityConfig` remains the access-control authority.
- Do not create a live link until a matching HTML route is available to that role. A disabled label may communicate planned navigation without sending users to a JSON API or forbidden route.
- Authenticated pages render inside the persistent dashboard sidebar; public pages retain the compact public navbar. Dashboard links target `#dashboard-content`, select the same stable region from full-page responses, and use the server-rendered `data-active-nav` value to synchronize active state after a swap.
- Closed mobile sidebars are inert. Move focus to dashboard content only for navigation; preserve focus for filters and slot updates. Promote only connected dialogs that are not already modal, and focus validation summaries after form replacements.
- For successful admin modal creation, return an empty modal region plus existing results and feedback as out-of-band fragments. Only mark those fragments `hx-swap-oob` in the mutation response; regular navigation must retain the feedback target.
- HTMX reservation/report redirects must save the existing Spring flash map for the destination before returning `HX-Redirect`.
- POST submissions use the shared request guard in `app.js`: disable submit controls while pending and show a spinner on the clicked control. Preserve submitter name/value and `formaction`; restore original disabled states after HTMX completion or browser Back navigation. GET filters do not lock.
- Queue sort links must keep the current page's route. Staff report pages retain completed/rejected reports in a separate history section; the dashboard only summarizes open work.
- Jenis Fasilitas and Jenis Blokir are reached through Pengaturan. Their management pages use `admin-settings` as the active navigation key and provide a return link to Pengaturan; keep their existing routes and forms.
- Supported `activeNav` keys currently include `minimal` (Pengguna dashboard), `facilities`, `reservations`, `dashboard`, `staff-reservations`, `staff-reports`, `users`, `admin-facilities`, and `recap`.

## Frontend build

Run `npm install` once, then `npm run build` after changing templates, shared CSS, or the HTMX version. Commit the generated `static/css/app.css` and `static/vendor/htmx.min.js` so Spring Boot serves production assets without a CDN.
