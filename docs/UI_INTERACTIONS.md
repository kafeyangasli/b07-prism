# Themes, account names, and table actions

PRISM keeps Tailwind 4.2.1 and the shared component tokens in `static/css/prism.css`. The light theme is the default token palette; `[data-theme="dark"]` supplies semantic colors and neutral shades. The permanent violet sidebar and authentication panels retain their brand palette. Use `bg-surface`, `text-content`, and semantic status colors for new surfaces, and `text-primary-contrast` for content on `bg-primary`.

`static/js/theme.js` runs synchronously before CSS in the shared layout. It reads the `prism-theme` preference, falls back to the system theme, and supports storage being unavailable. The shared `workspace :: themeToggle` fragment supplies the current-theme icon and an accessible switch label. `workspace :: themeLogo` selects the existing light/dark brand asset through CSS. Public, mobile, and desktop headers use these fragments.

Profile updates keep their normal POST/redirect route and gain HTMX enhancement. After the service transaction succeeds, the controller refreshes only the persisted display name in the current principal and saves the security context. The server-rendered dashboard content carries `data-navigation-name`; fresh HTMX responses update `[data-display-name]` elements using `textContent`. Rejected submissions retain the stored name. Cached history content cannot overwrite the current navigation name.

Complex table disclosures are progressively enhanced into native modal dialogs by `initializeActionDialogs` in `static/js/app.js`. Forms are moved, never copied: actions, fields, CSRF tokens, multipart encoding, and HTMX attributes remain attached. Dialogs stay beside their original trigger to preserve inherited HTMX context, and use the browser top layer instead of table space. Escape, outside clicks, close controls, focus return, and one-dialog-at-a-time behavior apply consistently. Without JavaScript, the original disclosure forms remain usable. Outside tables, opt into this enhancement using `data-action-dialog` on a `<details>` element.

Laporan list/detail actions share their page header with the heading. Shared section gaps, table padding, and `ui-table-actions` provide the same alignment as the other workspaces; report completion and rejection forms use dialogs.

Verification uses `npm run build`, JavaScript syntax checks, Maven tests (including real-session name regressions), and rendered Thymeleaf snapshots from `-Dprism.ui.snapshots=true`. The snapshot preview is read-only; backend form submissions are checked with MockMvc.

## Verification results (11 October 2026)

- Full Maven suite: 339 tests in 41 suites, zero failures, errors, or skips. After the final report-spacing adjustment, all 20 rendering checks passed again.
- Tailwind build, JavaScript syntax, template structure, and whitespace checks passed. Tailwind version and build configuration are unchanged.
- Browser checks: eight representative pages in both themes at 320, 768, and 1440 px, plus eight dashboard/calendar/form/history/admin pages in dark mode at 320 and 1440 px (64 combinations). No document overflow or duplicate IDs were found in the checked matrix.
- Confirmed both theme preferences survive reload. The synchronous theme script precedes CSS. Confirmed original multipart proposal upload fields and CSRF token inside the dialog, Escape/outside-click dismissal, and focus return.
- Proposal validation and facility editing keep table row dimensions unchanged on desktop. Report completion/rejection keep dimensions unchanged on mobile and desktop. A stable scrollbar gutter prevents dialog scroll locking from widening the page; report action groups use flex gaps so dialog margins remain centered.
- Dark text contrast: content 12.57:1, muted text 6.50:1, primary button 7.63:1; checked semantic status labels are at least 5.75:1. Input borders are 3.50:1 against the surface.
- Screenshots: `target/ui-preview/dark-proposal-dialog.png` and `target/ui-preview/dark-report-mobile.png`. Browser verification uses real Thymeleaf output with synthetic data. Live browser submissions and the separate MySQL concurrency profile were not run.
