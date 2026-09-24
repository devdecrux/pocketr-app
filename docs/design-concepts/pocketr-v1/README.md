# Pocketr design concepts — v1

Simple on the surface. Solid underneath.

Matching light and dark concepts for every current app page, with separate desktop and mobile PNGs. The eight pages added after the dashboard have 32 individual views and 16 combined comparison boards.

## Image index

| Page | Light theme | Dark theme | Desktop + mobile boards |
| --- | --- | --- | --- |
| Dashboard | [Desktop](pocketr-light-desktop.png) · [Mobile](pocketr-light-mobile.png) | [Desktop](pocketr-dark-desktop.png) · [Mobile](pocketr-dark-mobile.png) | [Light](pocketr-light.png) · [Dark](pocketr-dark.png) |
| Transactions | [Desktop](pages/transactions-light-desktop.png) · [Mobile](pages/transactions-light-mobile.png) | [Desktop](pages/transactions-dark-desktop.png) · [Mobile](pages/transactions-dark-mobile.png) | [Light](pages/transactions-light.png) · [Dark](pages/transactions-dark.png) |
| Accounts | [Desktop](pages/accounts-light-desktop.png) · [Mobile](pages/accounts-light-mobile.png) | [Desktop](pages/accounts-dark-desktop.png) · [Mobile](pages/accounts-dark-mobile.png) | [Light](pages/accounts-light.png) · [Dark](pages/accounts-dark.png) |
| Categories | [Desktop](pages/categories-light-desktop.png) · [Mobile](pages/categories-light-mobile.png) | [Desktop](pages/categories-dark-desktop.png) · [Mobile](pages/categories-dark-mobile.png) | [Light](pages/categories-light.png) · [Dark](pages/categories-dark.png) |
| Settings | [Desktop](pages/settings-light-desktop.png) · [Mobile](pages/settings-light-mobile.png) | [Desktop](pages/settings-dark-desktop.png) · [Mobile](pages/settings-dark-mobile.png) | [Light](pages/settings-light.png) · [Dark](pages/settings-dark.png) |
| Household | [Desktop](pages/household-light-desktop.png) · [Mobile](pages/household-light-mobile.png) | [Desktop](pages/household-dark-desktop.png) · [Mobile](pages/household-dark-mobile.png) | [Light](pages/household-light.png) · [Dark](pages/household-dark.png) |
| Sign in | [Desktop](pages/sign-in-light-desktop.png) · [Mobile](pages/sign-in-light-mobile.png) | [Desktop](pages/sign-in-dark-desktop.png) · [Mobile](pages/sign-in-dark-mobile.png) | [Light](pages/sign-in-light.png) · [Dark](pages/sign-in-dark.png) |
| Registration | [Desktop](pages/registration-light-desktop.png) · [Mobile](pages/registration-light-mobile.png) | [Desktop](pages/registration-dark-desktop.png) · [Mobile](pages/registration-dark-mobile.png) | [Light](pages/registration-light.png) · [Dark](pages/registration-dark.png) |
| Page not found | [Desktop](pages/not-found-light-desktop.png) · [Mobile](pages/not-found-light-mobile.png) | [Desktop](pages/not-found-dark-desktop.png) · [Mobile](pages/not-found-dark-mobile.png) | [Light](pages/not-found-light.png) · [Dark](pages/not-found-dark.png) |

## Design direction

- Cyan and amber Pocketr branding, quiet neutral surfaces, fine borders, and outline icons.
- Desktop sidebar with brand, divider, page navigation, and profile at the bottom.
- Mobile layouts use a menu-triggered sidebar and comfortable single-column controls.
- Transactions and accounts become readable lists on mobile; Household uses Members and Accounts sections.
- Sign in and Registration use a standalone public layout without the authenticated sidebar.
- Sample names, email addresses, and balances are fictional.

These are visual concepts created with the built-in image generation tool, not application screenshots or an implemented Nuxt UI migration. Use the repository's actual logo SVGs and Lucide icons for implementation. No application source files or dependencies were changed for this design work.

## Prompts and source references

- [Dashboard prompts](prompts.md)
- [Remaining page prompts](pages/prompts.md)
- Route scope: `pocketr-ui/src/router/index.ts`
- Features checked against the corresponding current files in `pocketr-ui/src/views/`.
- The initial Categories draft is kept in [drafts](pages/drafts/); the index links to the corrected final image.
