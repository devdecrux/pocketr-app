# Frontend Design System

This document describes the current Pocketr UI theming architecture. The goal is to preserve the existing visual design while making theme values, reusable component styling, and page composition easier to maintain.

## Audit Summary

The app already had a useful foundation:

- `pocketr-ui/src/main.css` defines Tailwind v4 theme mappings, shadcn-style semantic variables, and app-specific shell/sidebar variables.
- VueUse `useColorMode` provides the existing light, dark, and system modes.
- Reka UI primitives are mostly wrapped in `pocketr-ui/src/components/ui/*`, so pages rarely import Reka directly.
- Cards, inputs, buttons, dialogs, selects, dropdowns, tabs, and sidebar primitives already use shared wrappers.

The main maintainability issues were:

- Theme runtime was called directly from multiple places instead of through one app-level theme service.
- The auth pages duplicated the same theme-menu shell and form-field layout.
- Transaction and account type tab styling was repeated inline in pages.
- Some component-level CSS owned literal theme colors that belonged in theme token definitions.
- List rows, centered table states, feedback text, inline notices, and card title/action headers were repeated across pages.
- Current visual rules for shell gradients, table headers, sidebar controls, button states, dropdown borders, and tab controls were implicit in scattered classes.

## Architecture

Use four layers:

1. Design tokens
   - Semantic CSS custom properties such as `--background`, `--foreground`, `--card`, `--border`, `--primary`, `--app-sidebar-surface`, and `--app-button-bg`.
   - Tokens describe role and behavior, not literal color names.

2. Theme definitions
   - Theme values live in `pocketr-ui/src/main.css`.
   - The default preset is scoped with `:root[data-theme-preset='pocketr']`.
   - Dark mode keeps the existing `.dark` behavior through `:root.dark`.

3. Semantic reusable app components
   - Primitive wrappers live in `pocketr-ui/src/components/ui/*`.
   - App-level compositions live in `pocketr-ui/src/components/app/*`.
   - Pages should use these components before adding repeated Tailwind class bundles.

4. Feature/page composition
   - Pages should arrange data, forms, and flows.
   - Pages may use Tailwind for layout and responsive behavior.
   - Pages should not define reusable visual decisions, theme colors, or repeated primitive styling.

## Token Rules

- Name tokens by role: `--app-button-bg`, `--app-shell-overlay-start`, `--app-sidebar-divider`.
- Do not name tokens after literal colors, such as `--green-500` or `--dark-blue`.
- Keep actual color values in theme scopes in `main.css`.
- Component CSS should consume tokens with `var(...)`.
- Add a new token when a visual value is reused or represents a stable design role.
- Feedback, notice, and action-danger colors should use app tokens such as `--app-feedback-error-fg`, `--app-notice-info-bg`, and `--app-action-danger-fg`.
- Transaction amount colors should use `--app-transaction-amount-*` tokens through the transaction presentation utility.
- Keep one-off feature data colors out of the global theme unless they become reusable UI semantics.

The token contract is enforced by `pocketr-ui/src/__tests__/themeTokens.spec.ts`. When a token is added to one theme scope, add it to every theme scope in the same change.

## Current Theme Runtime

Theme runtime is centralized in `pocketr-ui/src/composables/useAppTheme.ts`.

It currently:

- preserves the existing light, dark, and system modes from VueUse `useColorMode`
- applies `data-theme-preset="pocketr"` on the document root
- exposes the current preset and available theme options from one place

When adding a new preset:

1. Add it to `APP_THEME_PRESETS` in `useAppTheme.ts`.
2. Add a matching theme scope in `main.css`, for example `:root[data-theme-preset='new-theme']`.
3. Add a dark variant if needed, for example `:root[data-theme-preset='new-theme'].dark`.
4. Define every required semantic token in the new scope.
5. Run `npm run test:unit -- --run src/__tests__/themeTokens.spec.ts` to verify the scope matches the contract.
6. Do not change component classes unless the new theme needs a new semantic role.

## Component Rules

Use `components/ui/*` for Reka-backed primitive wrappers. These components own primitive wiring, accessibility-preserving defaults, and base variants.

Use `components/app/*` for app-specific compositions:

- `AuthPageShell` owns the shared auth layout and theme menu trigger.
- `AppDialogContent` and `AppDialogBody` own repeated dialog header/body/footer structure while still using the existing dialog primitives.
- `AppFilterBar` owns repeated filter-row layout.
- `AppFormField` owns the standard label plus field spacing.
- `AppCardHeader` owns repeated card title/action and icon/title header layout.
- `AppPageHeader` owns simple page title/subtitle headers.
- `AppListItem` owns the standard bordered list-row treatment.
- `AppStateMessage`, `AppStatusText`, and `AppNotice` own repeated empty/loading/error/success/info/warning presentation.
- `AppStaticPanel` owns static standalone panels such as the not-found state.

Feature-specific reusable components should live next to the app component layer only when they are not general design primitives. For example, `CategoryColorPicker` centralizes category preset selection without turning category colors into theme tokens.

Before adding page-level styling:

- Check whether an existing `ui` component already supports the needed variant.
- If repeated markup appears in more than one page, create an `app` component.
- If repeated styling is primitive-level styling, move it to the relevant `ui` wrapper or `main.css` slot rule.
- If repeated styling is feature-specific composition, create a focused app component.

## Tailwind Usage

Tailwind remains the implementation tool for:

- layout
- spacing
- responsive behavior
- local composition
- state selectors

Tailwind should not be the source of truth for theme color values. Prefer token classes such as `bg-card`, `text-muted-foreground`, `border-border`, or CSS rules using `var(...)`.

## Implementation Phases

Phase 1: token/theme foundation

- Keep current light and dark visuals.
- Scope the current theme preset with `data-theme-preset`.
- Move reusable shell, sidebar, table, tab, control, and button values into tokens.

Phase 2: core semantic components

- Extract repeated shell and form-field patterns.
- Extract page headers, card headers, bordered rows, state text, feedback text, inline notices, and static panels where the same visual pattern is reused.
- Extract dialog and filter-row wrappers when pages repeat the same structure.

Phase 3: Reka wrapper cleanup

- Keep Reka imports inside `components/ui/*` unless a feature needs only Reka types.
- Move repeated slot styling for select, dropdown, tabs, dialogs, and popovers into wrappers or global slot rules.

Phase 4: targeted page migrations

- Migrate repeated page patterns incrementally.
- Start with auth, then settings/profile rows, then transaction/account dialog sections.
- Avoid broad rewrites that risk visual drift.

Phase 5: future-theme readiness

- Add theme presets by defining token values only.
- Later custom theme support should validate controlled overrides and map them onto existing semantic tokens.
- User-defined themes should not allow arbitrary component CSS.

## Routing

Page components should be lazy-loaded in `pocketr-ui/src/router/index.ts` so route views become separate chunks. Keep shared shell components, stores, and small cross-cutting utilities imported normally where they are needed.

## Follow-Up Opportunities

- Continue reducing one-off page composition where it becomes repeated, especially compact action controls and dialog form bodies.
- Add visual regression screenshots before larger page migrations.
