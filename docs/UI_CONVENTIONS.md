# PRISM UI Conventions

Use these rules for new Thymeleaf views so feature work remains visually and behaviorally consistent.

## Layout and type

- Start pages with `fragments/layout :: layout(pageTitle, activeNav, content)`; do not duplicate the document shell or navigation.
- Use `.ui-container` (`max-w-7xl`) for page content. Default page spacing is `py-8 sm:py-10` from the shared layout.
- Page title: `text-3xl font-bold tracking-tight`; section title: `text-xl font-semibold`; supporting copy: `text-sm` or `text-base text-slate-600`.
- Use slate page backgrounds, white surfaces, `border-slate-200`, `rounded-xl`, and `shadow-sm`. Avoid gradients and decorative motion.

## Shared controls

- Buttons always use `.ui-btn` plus one variant: `.ui-btn-primary`, `.ui-btn-secondary`, `.ui-btn-ghost`, or `.ui-btn-danger`.
- Destructive actions use `.ui-btn-danger`, a specific verb, and confirmation when data cannot be restored.
- Fields use `.ui-label` and `.ui-input`; help text uses `.ui-hint`. Add `.ui-input-error`, `aria-invalid="true"`, and a nearby `.ui-field-error` when invalid.
- Place `fragments/feedback :: formErrors` inside a `th:object` form for the validation summary.

## Surfaces, tables, and empty states

- Cards use `.ui-card` with an inner `.ui-card-body`.
- Wrap tables in `.ui-table-wrap` and apply `.ui-table` to the table. Keep action columns rightmost and use horizontal scrolling on narrow screens.
- Empty states use `.ui-empty-state`, a short title, one helpful sentence, and at most one next action.
- Pagination should preserve active filters, use normal links by default, and expose the current page with `aria-current="page"`.

## Status badges

Render enums with `fragments/status :: badge(status)`. The badge always includes text; color is supplementary.

| Meaning | Values | Treatment |
| --- | --- | --- |
| Awaiting work | `PENDING`, `NEW`, `SCHEDULED` | Amber |
| In progress / usable | `APPROVED`, `ACTIVE`, `IN_PROGRESS` | Blue |
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
- Supported `activeNav` keys currently include `minimal` (Pengguna dashboard), `facilities`, `reservations`, `dashboard`, `staff-reservations`, `staff-reports`, `users`, `admin-facilities`, and `recap`.

## Frontend build

Run `npm install` once, then `npm run build` after changing templates, shared CSS, or the HTMX version. Commit the generated `static/css/app.css` and `static/vendor/htmx.min.js` so Spring Boot serves production assets without a CDN.
