# PRISM UI

The UI uses Tailwind CSS 4, local assets, and server-rendered Thymeleaf views.

## Brand and tokens

`src/main/frontend/app.css` is the source of truth. Its `@theme` defines:

- Original logo violet `#7469B6` and lavender accent `#AD88C6`.
- Primary action `#5D5098`, hover `#4B407C`, subtle fill `#F3F0FA`.
- Page `#F8F7FA`, surface `#FFFFFF`, border `#E0DCE7`.
- Main text `#292333`, muted text `#70677D`.
- Semantic success, warning, danger, information, and disabled tokens, with matching fills and borders.

Use semantic tokens or the shared `ui-*` classes instead of adding unrelated color families. Lavender is an accent, not body text. Original SVGs remain unchanged; byte-identical copies under `static/images` use the existing publicly permitted asset route.

## Components

- `fragments/layout.html`: assets, skip link, feedback region, footer, and page container.
- `fragments/navigation.html`: role-based links, native mobile disclosure, active-page indicators, authentication actions.
- `fragments/page.html`: reusable page heading.
- `fragments/feedback.html`: flash messages and bound form errors.
- `fragments/status.html`: text-labeled semantic badges and a neutral fallback.
- `ui-btn-*`: primary, secondary, neutral, destructive, ghost, and disabled actions.
- `ui-input`, `ui-label`, `ui-form-*`: fields, validation, uploads, and form actions.
- `ui-table-*`: desktop tables and labeled records below 1024px. Keep `data-label` values in sync with headings. Action cells use `ui-table-actions`.

Navigation remains usable without JavaScript. The script initially collapses it on mobile and opens it at desktop widths. Existing HTMX and CSRF behavior is retained. Dismissing a global HTMX error hides its reusable target instead of removing it.

## Build and verification

Run `npm ci` if dependencies are missing, then `npm run build`. Keep generated `static/css/app.css` alongside its source.

Run `./mvnw test` (Windows: `mvnw.cmd test`). `UiRenderingIntegrationTest` renders all 11 views using H2 and checks public assets, role navigation, form bindings, CSRF, conditional cancellation, exports, and the HTMX result target. Optional `-Dprism.ui.snapshots=true` writes fixture-rendered HTML under ignored `target/ui-preview` for browser review. These snapshots are test artifacts, not a live server.

Responsive review covers mobile, tablet, laptop, and desktop widths. No controllers, services, repositories, schema, or authorization rules were changed for this redesign.
