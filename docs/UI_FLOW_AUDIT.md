# PRISM flow audit

Baseline: server-rendered Thymeleaf, violet/lavender brand tokens, system sans type,
white bordered cards, small shadows, rounded controls, textual status badges, public
navbar and role-aware dashboard sidebar. Preserve these conventions.

## Findings before implementation

| Priority | Journey | Finding and smallest coherent improvement |
| --- | --- | --- |
| Critical | Guest → catalogue → detail → availability | The availability route exists but facility detail has no link to it. Add an explicit schedule action and style the existing date filter and slots. Keep applicant names and purposes private. |
| Critical | Admin → blockage types | Sidebar leads to a JSON API. Add a small MVC adapter using existing type services and shared components; preserve the API. |
| Critical | Admin → create account / facility | Successful modal submissions return only a flash message; the list remains stale. Include the updated existing results fragment and an empty modal region in the response. |
| High | User → modal submission → detail | HTMX redirects skip Spring's normal redirect flash handling and lose success feedback. Save the existing flash map for the destination before returning HX-Redirect. |
| High | Guest → combined facility filters | Supplying dates silently ignores type/location/capacity; an incomplete interval is silently ignored. Compose existing query results and show actionable interval errors. |
| High | User → reservation → detail → cancel | Status is buried, proposal validation is absent, cancellation is immediate, and the 24-hour restriction is undisclosed. Show status guidance and a consequence-aware confirmation using existing cancellation authority. |
| High | Staff → report → blockage | Staff must remember and retype facility and report IDs. Add a link from report detail that prefills trusted report context using existing services. |
| High | Keyboard → mobile navigation / slot update | Hidden sidebar links remain keyboard-focusable; every HTMX settle moves focus to the page even for filters and slot selection. Make closed sidebar inert and scope focus changes to navigation or replaced controls. |
| Medium | Public availability on narrow screens | Unstyled inputs and unlabeled table cells conflict with the shared mobile table transformation. Reuse slots with textual statuses, visible reasons and a date caption. |
| Medium | User → report / reservation forms | Required/optional guidance and nearby validation are inconsistent. Add field feedback and submission expectations without changing validation rules. |
| Medium | Staff → blockage preview / early completion | Impact counts blend into ordinary paragraphs; fields are crowded; completion consequences are undisclosed. Group fields and emphasize impact with existing alerts and stats. |
| Medium | Admin → facility types / recap | Inline edit inputs are unlabeled, repeat edits dominate scanning, and export context is vague. Use labelled disclosure edits, a controlled scroll recap table and the applied period. |
| Low | Histories / authentication | Empty histories lack next-step guidance; registration lacks field-specific errors. Reuse existing empty states and feedback. |

## Scope and validation

No new framework, dependencies, schema changes, domain rules, transitions, or
transaction changes. MVC additions only bridge existing services to HTML journeys.
Validate role authorization, CSRF, public privacy, combined filters, submission,
proposal processing, reports, blockage preview/confirmation, admin operations and
exports with integration tests. Inspect rendered pages at desktop, tablet and
mobile sizes.

## Changes implemented

| Page / workflow | Change | Usability rationale |
| --- | --- | --- |
| Facility catalogue and details | Combined filters now apply together; incomplete/reversed ranges produce feedback in the results region. Details link to availability; inactive facilities explain why reservation actions are unavailable. | Prevent misleading search results and schedule dead ends. |
| Public availability → reservation | Styled date filter, responsive slot grid, text status legend and visible public blockage reasons. Missing date defaults to today using the application clock. The selected date carries into the reservation form. | Make availability understandable and avoid re-entering context. Applicant identity and purpose remain private. |
| Reservation form and detail | Show operational rules, proposal requirements and the approval step before submission. Put facility/status first and start/end times together; explain statuses and proposal validation. | Users can distinguish an application from a confirmed booking and identify their next step. |
| Cancellation and other consequential actions | Reveal cancellation consequences and an explicit confirmation checkbox; display the existing 24-hour deadline and hide ineligible cancellation forms. Explain report rejection, facility/account/type deactivation and early blockage completion. | Reduce accidental actions and predictable server rejections without changing service rules. |
| Report forms/history/detail | Field errors, descriptive help, required-field guidance, grouped detail metadata, status guidance and contextual empty states. Staff report detail retains the staff navigation key. | Improve submission feedback and orientation across the reporting journey. |
| Staff reservation processing | Inline purpose disclosure, proposal-validation guidance, conflict counts and a Segera badge when the approval deadline is within 24 hours. | Bring relevant context before approval or rejection without an extra page. |
| Report → blockage | Prefill facility, report number and active REPAIR type from the existing staff-authorized report service. Group form controls, distinguish public reason from internal note and emphasize both impact counts. | Remove remembered IDs and make cascading consequences easier to review. |
| Admin type management | Native blockage-type list/create/edit/deactivate adapter using existing services; labelled disclosure edits and shared status badges for type lists. | Replace the raw JSON dead end and keep repeated operations scanable. |
| Admin modal creation and user modal submission | Updated results and feedback fragments close successful admin dialogs and refresh lists. Save flash maps before HTMX redirects for reservations and reports. | Show the completed action immediately and retain success feedback. |
| Navigation, focus and responsive lists | Closed mobile drawer is inert; content behind an open drawer is inert. Filters retain focus, slot swaps focus the selected control, validation focuses its summary, and dialogs are not reopened on unrelated updates. Catalogue filters collapse on smaller screens. | Keep keyboard users oriented and let mobile users reach results sooner. |
| Recap/export | Display applied period and export scope. Use a labelled, keyboard-focusable horizontal table region with a mobile scrolling hint. | Preserve column comparisons and show exactly which data will be exported. |

## Design language preservation

Retained the PRISM logos, violet/lavender tokens, system sans typography, dark
sidebar, light public navbar, white bordered cards, existing radii and shadows,
button variants, spacing utilities and textual status badges. Reused shared
feedback, status and page fragments, existing slots and native disclosures/dialogs.
No frontend framework, aesthetic dependency or animation was added.

## Responsive verification

- Desktop: 1440 × 900; catalogue cards, admin dialogs, type management, reservation
  summary and export filters remain consistent with the baseline.
- Tablet: 768 × 1024; staff reservation context and blockage impact preview remain
  readable. Multi-line mobile table cells now keep all content beside their label.
- Mobile: 390 × 844; catalogue filters collapse, slots use two columns, drawer
  contents are removed from keyboard navigation when closed, and reservation
  dialogs retain the existing full-screen behavior.
- Recap at mobile width: table scroll width 832 px within a 342 px container;
  page width 375 px within a 390 px viewport. The page itself does not overflow.

## Verification results

- 119 distinct targeted tests passed across 13 classes: 118 in the combined
  workflow run plus one additional large-list/navigation regression test in the
  final 16-test rendering run. No failures or skipped tests in those final runs.
- Coverage includes guest availability/privacy and combined filtering; user
  reservation submission, proposal constraints, cancellation eligibility,
  report/photo workflows and flash redirects; staff proposal validation,
  approval/rejection and blockage preview/confirmation; admin management,
  authorization, CSRF, recap filtering and CSV/XLSX/PDF exports.
- Browser checks on an isolated H2 preview completed guest discovery/filter/detail/
  availability, user reservation submission/detail, staff approval and report-to-
  blockage confirmation, admin facility create/edit and category creation, and
  filtered CSV download. The downloaded CSV contained only the selected facility.
- Checked empty states, duplicate-code validation with retained inputs, long
  facility names, a 25-user rendered list, disabled proposal approval, inactive
  facilities, keyboard focus and narrow-screen overflow.
- `npm run build`, JavaScript syntax check and `git diff --check` passed.
- Test fixture repairs were limited to relevant stale facility-type fixtures and
  assertions, including the generated CSS filename. Domain code was not changed
  to accommodate tests.
- Validation used Java 26 compiling for release 25 and isolated H2 data. A full
  production MySQL/migration run and exhaustive manual assistive-technology audit
  were not performed. Temporary browser data was separate from application data.

## Important files modified

- Shared styles/scripts: `static/css/prism.css`, generated `static/css/app.css`,
  `static/js/app.js`.
- Shared fragments: `templates/fragments/navigation.html`, `feedback.html`.
- Guest/user templates: facility list/detail; reservation availability/form/detail/
  history; report form/list/detail/actions; registration.
- Staff templates: dashboard, reservations, reservation-actions, blockages.
- Admin templates: users, facilities, facility-types, new blockage-types, recap.
- MVC adapters: `FacilityController`, `PublicFacilityAvailabilityController`,
  `ReservationController`, `ReportPageController`, `BlockageController`,
  `StaffDashboardController`, `AdminUserController`, `AdminFacilityController`,
  new `AdminBlockageTypePageController`.
- Staff presentation data: `PendingReservationRow`, `StaffDashboardService` only
  to include the purpose already stored on a reservation.
- Relevant rendering/workflow tests and `docs/UI_CONVENTIONS.md`.

## Requested follow-up fixes

- Shared POST guard: clicked submit controls show an accessible busy state and
  spinner, form submit controls are disabled while pending, repeat submissions
  are blocked, and original states are restored after HTMX completion or browser
  Back navigation. Native submitter values and alternate action URLs are preserved.
- Dashboard Waktu masuk/Waktu penggunaan links remain on `/staff/dashboard`;
  the reservation page retains its own sort routes.
- Staff Laporan now keeps resolved and rejected reports in a visible history
  section, with status badges and links to their existing detail pages.
- Validation: 33 focused rendering/report/administration tests passed. Delayed
  browser checks confirmed one request per double-click, native name/value and
  formaction preservation, restoration after success/server failure/Back, and
  loading on standalone HTMX POST buttons. CSS build and JavaScript syntax passed.

## Remaining recommendations

- Add server-side pagination and richer history/queue filtering for large data
  sets; this needs query/controller work beyond layout refinement.
- Preserve a selected facility through guest login; the current security success
  handler intentionally returns every role to its home page. A redirect contract
  change deserves a separate authentication-flow review.
- Provide a complete non-JavaScript slot-selection fallback; the existing
  reservation flow depends on HTMX to load slots and would need additional form
  routing to support that journey fully.
- Consider reservation/report activity timelines and account-verification
  notifications when backend support is available. These exceed this refinement.
