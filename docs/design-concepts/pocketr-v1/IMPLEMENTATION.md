# Pocketr UI migration runbook

## Goal

Rebuild `pocketr-ui` **mobile first** to match `docs/design-concepts/pocketr-v1/` exactly, using **Nuxt UI v4 in the existing Vue + Vite app**. Improve performance without changing behavior. Do not migrate to Nuxt.

## Non-negotiable rules

- **Write scope: `pocketr-ui/**` only.** Everything else is read-only, including `docs/design-concepts/**`, backend services, infrastructure, CI and repository-level configuration.
- The approved PNGs are the **only visual source of truth**. Do not redesign, guess, simplify, or add UI. If anything is unclear, stop and ask.
- Preserve every working feature: APIs, stores, routes/guards, validation, permissions, calculations, loading/error/empty states, i18n, responsive behavior, and accessibility.
- Work on **one numbered phase per run**, then test, report changed files/deviations, and stop for review.
- This is only the agreed frontend refactor and visual migration. Do not change product behavior, backend code, API contracts, data models or unrelated code.
- Keep pages buildable throughout the migration; untouched pages must remain unchanged.

## Final UI stack

- Use `@nuxt/ui` through `@nuxt/ui/vite` and `@nuxt/ui/vue-plugin` ([Vue setup](https://ui.nuxt.com/docs/getting-started/installation/vue)). Keep Vue, Vite, Router, Pinia, i18n, VueUse and `ky`.
- Keep `echarts` + `vue-echarts`.
- Keep `tailwindcss`; Nuxt UI requires it. Import only `tailwindcss` and `@nuxt/ui` in the main CSS.
- Pocketr code must not directly use Reka UI, TanStack Table, `lucide-vue-next`, CVA, `clsx`, `tailwind-merge`, or copied UI primitives. Nuxt UI may use internal dependencies.
- Use Nuxt UI components directly. Create shared components only for Pocketr-specific layout/behavior, organized under `components/layout`, `components/shared`, `components/forms`, and `components/charts`. Do not create thin wrappers around `UButton`, `UCard`, etc.
- Replace tables with `UTable`, desktop dialogs with `UModal`, mobile form sheets with `UDrawer`, and use Nuxt UI form/navigation/input components.
- Enable Nuxt UI Vite component detection and bundle only icons actually used by Pocketr.

## Required agent tooling

- The official **Nuxt UI skill**, **Nuxt UI MCP server** and **Playwright CLI skill** are user-global prerequisites. The agent must verify they are available before repository preflight, but must not install or reconfigure global tooling.
- Invoke the `nuxt-ui` skill for every implementation phase. Use the Nuxt UI MCP server for current component APIs, props, slots, events and examples; never rely on memory or guess an API.
- Use `https://ui.nuxt.com/llms.txt` only as a documentation fallback. Never load `llms-full.txt` in full; retrieve only the specific information needed.
- Use Playwright CLI to inspect the running UI, exercise workflows and capture review screenshots. Keep `@playwright/test` for repeatable E2E and approved visual-regression tests.
- If any required integration is unavailable, stop and report exactly what is missing. Do not substitute another UI or browser-automation tool.

## Visual rules

- Match every page's light/dark desktop/mobile PNG. No gradients, glass effects, purple, decorative additions, or alternate layouts.
- Light: `#FAFAFA` canvas, white surfaces, `#E5E7EB` borders, `#172124` text, `#67747C` muted, `#0891B2` cyan, `#F59E0B` amber.
- Dark: `#101214` canvas, `#15191D` main, `#111518` shell, `#1C2227`/`#242C32` surfaces, `#30383F` borders, `#EDF2F5` text, `#A0ACB7` muted, `#22D3EE` cyan.
- Copy approved files from `logo-assets/` into `pocketr-ui/public/` without editing them. Use the same transparent logo in both themes; never recolor, redraw, crop, flatten transparency, add a background, or use the old logo.
- Use `pocketr-logo-64x64.png` for the normally rendered 32px app mark, the supplied favicon files in `index.html`, and `apple-touch-icon.png` for Apple touch. Keep explicit image dimensions. Do not load the master/1024px file for small UI or add PWA behavior only to use the Android files.
- Create transaction/account/category: X + Cancel, consistent scrim, desktop modal, mobile drawer, Escape/swipe close, and no close on backdrop click. Share one form component between desktop/mobile.
- Create household is an inline page state: no modal, scrim, X, or Cancel.

## Mobile-first and performance rules

- Build and approve the `352x856` mobile concept first; then add desktop behavior for `1174x856`. Never shrink a desktop table/form into mobile.
- Share data, state and form components across breakpoints; change only presentation. Do not duplicate page logic for mobile and desktop.
- Preserve route lazy-loading. Lazy-load ECharts and page-only code; never import charts into the app shell. Avoid unnecessary watchers, renders and duplicate requests.
- Record production JS/CSS sizes after every phase. Any unexplained regression must be fixed or reported before approval.

## Execution phases

Before each phase: read the target view, its tests, translations, and all matching PNGs. Capture current behavior. Implement mobile first, then desktop, for only that phase. Use a temporary route/layout flag so migrated and legacy pages can coexist without mixing UI systems inside one page.

### Repository preflight

The main agent must complete this once before spawning the Phase 0 subagent:

1. Run `git status --short`. If the working tree is not clean, stop and ask; never stash, commit, discard or overwrite user work without approval.
2. Run `git fetch origin`, `git switch master`, then `git pull --ff-only origin master`.
3. If local branch `app-ui-redesign` already exists, stop and ask; never delete or recreate it automatically.
4. Create and switch with `git switch -c app-ui-redesign`.
5. Confirm the current branch is `app-ui-redesign` and the tree is clean. All implementation phases must run on this branch; never implement on `master`.

### Agent orchestration

- After repository preflight, the main agent coordinates and reviews; it must spawn **one fresh subagent for each numbered phase**, including Phase 0.
- Give the subagent only this runbook, the phase number, target files/images/tests and the current repository state. Require it to use the Nuxt UI skill/MCP and Playwright CLI. The subagent must read the references itself and modify only that phase.
- Run only one phase subagent at a time. The main agent monitors it, reviews its diff, verifies tests/visual checks and sends phase-specific corrections back to the same subagent.
- After the phase passes, the main agent reports to the user and waits. Spawn a new subagent for the next phase only after explicit user approval.

0. **Foundation only** — install/configure Nuxt UI; add global Pocketr tokens/theme, approved logo/favicon assets and the temporary per-route migration switch. Establish the build-size baseline. No page redesign and no legacy deletion.
1. **Dashboard + authenticated shell** — `pocketr-*.png`; sidebar/header/mobile navigation and dashboard only.
2. **Sign in** — `pages/sign-in-*.png`.
3. **Registration** — `pages/registration-*.png`.
4. **Transactions** — `transactions-*` and `create-transaction-*`; preserve every transaction type, filter, pagination, CRUD action and validation.
5. **Accounts** — `accounts-*` and `create-account-*`; preserve types, balances, currency handling, rename/archive and validation.
6. **Categories** — `categories-*` and `create-category-*`; preserve CRUD, colors and validation. Never show edit and create overlays together.
7. **Settings** — `settings-*`; preserve profile, language, theme, rollover and household actions.
8. **Household** — `household-*` and `create-household-*`; preserve members, invitations, account sharing, roles and permissions.
9. **Not found** — `not-found-*`.
10. **Cleanup only after all pages are approved** — remove the temporary migration switch, `components/ui/**`, obsolete `components/app/**` and unused legacy components. Remove direct legacy dependencies only after `rg` proves no imports remain. Keep ECharts. Run the full suite.

## Required gate after every phase

- Use Playwright CLI to compare light/dark screenshots at exactly `352x856` and `1174x856` against the target PNGs. Never update a baseline to hide a mismatch.
- Run relevant unit and Playwright tests, then `npm run type-check` and `npm run build`.
- Report production JS/CSS sizes versus the previous approved phase.
- Verify keyboard/focus behavior and the phase's complete user workflow.
- Report tests, changed files, and any mismatch; **stop and wait for approval**.
