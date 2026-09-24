# Remaining Pocketr page concepts

Built-in image generation tool. Each page has a light and dark board, plus separate desktop and mobile extracts. Based on current Vue route pages; sample data is fictional. No app implementation changes.

## Shared light-theme prompt

Use case: ui-mockup. The supplied approved Pocketr LIGHT dashboard image is the edit target and exact design-system/layout reference. Create a new page in this SAME app, not a new visual direction. Output one crisp high-fidelity LIGHT-THEME concept board, desktop left and mobile right, matching the reference canvas aspect ratio and frame positions exactly (reference 1586x992; desktop outer frame x20 to1190, y107 to958, mobile outer frame x1221 to1567, y107 to958). Keep the same logo, font, cyan accent, warm-white surfaces, fine neutral borders, subtle 10px corner radii, Lucide-style thin outline icons, spacing, front-on flat framing, and restrained financial-app aesthetic. No device hardware. No gradients, no decorative 3D, no new purple, no invented branding.
Change the page content and active navigation as requested below. All other shared shell styling stays the same. Board heading at top is "Pocketr / PAGE / Light" substituting the requested page name, and below is the EXACT motto "Simple on the surface. Solid underneath."
For authenticated pages keep the desktop left sidebar logo + Pocketr, divider, five nav entries Dashboard, Transactions, Accounts, Categories, Household. Highlight only the current page. Bottom profile AM / Alex Morgan / alex@example.com with chevron stays anchored at bottom. CLOSE the profile popup from the reference, remove that popup fully on these new screens. Do not add a Settings sidebar item; Settings is accessed by the profile. Top app header shows page name left and Personal context selector right unless specified otherwise. Do not retain dashboard balance cards, charts, transaction list, or add-transaction button unless explicitly requested for this page.
Mobile top bar: hamburger, wallet logo and Pocketr, AM avatar; sidebar closed, no permanent sidebar, no bottom navigation. Reflow into actual readable touch-friendly single-column mobile controls. Do not shrink desktop tables onto mobile. Show only as many rows as fit naturally. Use bottom safe area for the page-specific primary action when specified. Both frames fully visible and all text crisp. Fictional people and financial data only. No extra features beyond the following spec.
PAGE SPECIFICATION:


## Transactions

Authenticated page, Transactions active. Desktop heading "Transactions", subtitle "Every movement, clearly recorded.", primary "+ Add transaction". Header right Personal selector. Under heading a clean filter bar with search "Search descriptions", date "1–30 Sep 2026", selectors "All accounts" and "All categories". The rest is a spacious transaction table with columns Date, Description, Type, Category, Amount, and subtle chevron/delete actions. Six rows newest first:
20 Sep | Grocery market | Expense | Groceries | −€46.80
20 Sep | City transport | Expense | Transport | −€24.00
19 Sep | Coffee shop | Expense | Other | −€4.50
18 Sep | Savings transfer | Transfer | — | ↔ €250.00
15 Sep | Credit card payment | Debt payment | — | −€120.00
01 Sep | September salary | Income | — | +€3,500.00
Use cyan/neutral badges and muted emerald only for income, expenses near ink not alarming red. One grocery row expanded below itself with subdued inset "Transaction details", lines "Daily account → Living expenses" and "Category: Groceries"; details are quiet, no ledger jargon occupying the main list. Delete uses small trash icon; NO edit pencil, no export/import/bank sync. Bottom row "1–6 of 6", rows per page "15", previous/next arrows.
Mobile title Transactions, small Personal selector, search full width, second compact row "September 2026" and "Filters" button with sliders. List grouped "Today", "Yesterday", "Earlier this month", readable merchant name and category, amount aligned right, row chevrons; first five matching transactions fit. Transfer shows bidirectional arrow and neutral €250.00, salary below naturally if it fits. Sticky full-width "+ Add transaction" bottom button. No open create modal, no random summary cards.

## Accounts

Authenticated Accounts page, Accounts active. Heading "Accounts", subtitle "A clear home for every balance.", primary "+ New account". Filters "All types", "All currencies" compact outlined dropdowns. Two modest summary panels "Money available" "€2,845.00" and "Debt outstanding" "€320.00"; muted helper "EUR accounts". Below a spacious clean account table with columns Account, Type, Currency, Balance, Actions. Six rows:
Daily account | Asset | EUR | €1,845.00
Savings | Asset | EUR | €1,000.00
Credit card | Liability | EUR | €320.00
Salary | Income | EUR | €3,500.00
Living expenses | Expense | EUR | €1,075.00
Other expenses | Expense | EUR | €80.00
Each row has modest matching outline icon, a subtle type badge, right-aligned tabular amount, edit pencil and archive box outline actions. Liability badge muted amber; no alarming red. No bank logos, masked card numbers, syncing, unsupported account delete, account charts or equity account. Useful whitespace below list.
Mobile title Accounts with Personal selector, summary available and owed in two compact cards or two rows, two dropdown filters, then full-width list rows for the same six accounts (show first four/five if needed), balance right, type and EUR beneath name, overflow chevron for account actions. Sticky "+ New account" bottom button. Match dashboard typography and calm hierarchy.

## Categories

Authenticated Categories page, Categories active, header Personal. Heading "Categories", subtitle "Small labels. A clearer picture.", primary "+ New category". A modest secondary line "Your personal spending categories". Main content a broad single bordered list card with columns Category, Created, Actions. Six alphabetically sorted spacious rows, each with small circular color dot: Groceries (muted green), Health (muted rose), Housing (cyan), Leisure (muted blue), Other (amber), Transport (slate). All dates "12 Sep 2026". Each row has small edit pencil and delete trash outline actions. Do not invent transaction counts, spending totals, budgets, category icons chosen by user, shared categories, AI suggestions or category search.
Alongside the list on desktop, a narrow tasteful sample editor panel titled "Edit category" with close x, field Name value "Groceries", Color with 8 small color swatches in two rows, green swatch selected with check, additional "No color" option, footer Cancel and cyan "Save changes". This is the currently open category edit panel; keep list visible without dimming it. Swatches cyan green blue rose amber slate neutral plus no-color, no purple.
Mobile shows Categories heading and subtitle, all six list rows with colored dots and edit pencil / overflow affordance, generous touch targets and creation date secondary. Keep editor CLOSED on mobile so list remains usable. Sticky "+ New category" bottom action. No unnecessary metric cards. Keep composition elegant and spare.

## Settings

Authenticated Settings page accessed from bottom profile: none of the five sidebar nav rows selected; profile footer softly highlighted cyan. Header Settings; Personal selector right. Heading "Make Pocketr yours", subtitle "Your profile and everyday preferences."
Desktop use an organized 2-column settings layout. Wider left:
Profile section with AM avatar, read-only "Alex Morgan", read-only "alex@example.com", a button "Change photo", small helper "JPG, PNG, GIF or WebP · Up to 5 MB". Names and email must NOT be editable inputs. Preferences section: Language dropdown "English", secondary note "English, Bulgarian, German"; field "Month starts on" value "1", helper "Choose when your monthly spending period begins."; cyan "Save preferences" action.
Right column:
Appearance section with three equal preview tiles and icons "Light" selected, "Dark", "System", restrained border and cyan check state; note "Choose what feels comfortable."
Household section: household outline icon, "Morgan household", role pill "Owner", small secondary "Shared finances, together.", outlined "Manage household" and subtle muted red text "Leave household". User already belongs to a household: do NOT show create-household form or pending invitations.
Mobile title Settings; single column Profile compact, photo action, Language select, Month starts on field with concise helper, Appearance Light/Dark/System selector, household card lower with Manage action if it fits. Bottom full-width "Save preferences" button. No account deletion, password reset/change, editable profile names, billing, notifications or security features not in app. No fictitious settings tabs.

## Household

Authenticated household settings page, Household active. Header "Household", context "Morgan household" selected (users icon) instead of Personal. Heading "Morgan household", subtitle "Shared finances. Individual control.", small role badge "Owner".
Desktop content: compact horizontal top settings row "Month starts on" numeric input "1", helper "A shared monthly spending period.", outlined "Save" button.
Then 2 columns. Left section "Members" with avatars and 3 rows: AM Alex Morgan / alex@example.com / Owner / Active; JM Jamie Morgan / jamie@example.com / Member / Active; ST Sam Taylor / sam@example.com / Member / Invited (amber). No role-changing dropdowns, remove-member or cancel-invite buttons. Below is "Invite a member" with helper "Invite someone who already has a Pocketr account.", Email field placeholder "name@example.com", cyan "Send invitation".
Right section "Shared accounts", helper "Visible to everyone in this household.", two clear rows Daily account / Shared by Alex Morgan, and Household savings / Shared by Jamie Morgan. No fake shared account balances.
Below is "Your accounts", helper "You choose what to share.", three rows: Daily account / EUR / Shared / "Unshare"; Savings / EUR / Private / "Share"; Credit card / EUR / Private / "Share". Buttons understated, Share cyan outline and Unshare neutral. No controls for changing another user's shares. No delete household, rename, member-role editing.
Mobile title "Morgan household" and Owner small; compact local section tabs "Members" selected, "Accounts" unselected (responsive content organization only). Show Month starts on value1 and Save row, Members rows with legible status/roles, Invite a member field and full-width Send invitation action. Do not cram other accounts into mobile active Members tab. Make desktop and mobile clearly same state and same product.

## Sign in

PUBLIC unauthenticated sign-in route. IMPORTANT remove desktop sidebar, profile, personal selector, dashboard header, mobile hamburger and mobile avatar: visitors are not signed in. Keep the board desktop frame and mobile frame same outer positions. Within each frame use clean standalone authentication layout in existing Pocketr style.
Desktop simple top header wallet logo + Pocketr left and small sun icon theme selector right. In the center a narrow white subtly bordered form card, generous breathing room, small wallet outline icon, heading "Welcome back", subtitle "Sign in for a little more clarity." Then visible labels Email and Password, Email field placeholder "alex@example.com", Password field empty with masked dots as sample, full-width cyan "Sign in". Under button "New to Pocketr? Create an account" with cyan link. Quiet centered motto "Simple on the surface. Solid underneath." below card. Keep it minimal, no illustrations, no marketing hero, no chart.
Mobile same standalone logo header top, theme sun icon upper right, generous top padding, heading Welcome back, same subtitle, single-column email and password fields with large touch targets, full-width Sign in, create account link, exact motto wrapped naturally lower down. Mobile form fits comfortable screen. No sidebar, hamburger, avatar, Google/social login, biometric login, forgot password link, remember-me checkbox, signup consent, or unsupported controls. This is a calm normal success-ready input state with no error message.

## Registration

PUBLIC unauthenticated registration page. Remove desktop sidebar, profile, personal selector, dashboard header, and mobile hamburger/avatar. Keep outer desktop/mobile frames and shared cyan Pocketr visual identity.
Desktop small top brand header logo + Pocketr left, sun theme icon right. Center a white bordered card about 420 CSS px wide titled "Create your account", subtitle "A simpler way to see your money." Form labels First name, Last name side by side with example values Alex and Morgan; Email full width with alex@example.com; Password full width masked dots; Confirm password full width masked dots. All fields have permanent visible labels. Full-width cyan "Create account" button. Below "Already have an account? Sign in" with cyan link. Subtle motto below card exact "Simple on the surface. Solid underneath."
Mobile logo header and sun theme icon, single-column form with First name then Last name then Email then Password then Confirm password (do NOT squeeze two name inputs into half widths). Large readable labels, touch-friendly input heights, title "Create your account", short subtitle, full-width Create account and Sign in link. Fit inside frame with comfortable normal spacing; do not add a second form, social login, password rules unsupported by data, newsletter/terms checkboxes, extra fields or footer clutter. Restrained authentication page matching sign-in and approved dashboard.

## Page not found

Authenticated 404 / Page not found route. Keep the same sidebar and bottom profile, none of the five nav rows selected. Main header "Page not found", context Personal. Close profile popup. Main content has a small understated outlined file-search Lucide icon, medium cyan "404", heading "This page wandered off.", explanation "The page you’re looking for doesn’t exist or has moved.", and a single cyan button with left arrow "Back to dashboard". Center this compact empty state in generous whitespace in the main area. No charts, cards of metrics, transaction action, decorative illustrations, mascots, search box, support links, second action or technical stack details.
Mobile same Pocketr header with hamburger and AM avatar, compact central 404 empty state, exact matching heading and sentence wrapped to 2–3 readable lines, full-width "Back to dashboard" button within content column. No bottom nav. Preserve very simple approachable style and all reference borders, colors, proportions. Board title "Pocketr / Page not found / Light" and same motto.

## Shared dark-theme edit prompt

Use case: style-transfer. Edit the supplied Pocketr LIGHT page concept board into an exact matching DARK-THEME version. This is an edit target. Preserve EVERY page-specific word, all sample data, entire content and structure, component and chart positions, sidebar selection, form layout, desktop/mobile composition, exact canvas aspect ratio and outer-frame boundaries. Do not crop, resize, add elements, or revert content to a dashboard.
Only change theme colors and states. Board heading suffix "/ Light" becomes "/ Dark". Keep motto verbatim "Simple on the surface. Solid underneath." Neutral charcoal board #101214; sidebar #111518; main background #15191D; raised card surfaces #1C2227, popovers #242C32; fine borders #30383F; main text #EDF2F5; muted text #A0ACB7. Accent #22D3EE; active nav dark cyan tinted #11333E. Primary buttons bright cyan with very dark #082F3A text. White fields become dark neutral fields with visible borders. Icons become light neutral or cyan. Pocketr logo shape and cyan wallet/amber banknote unchanged. Preserve category swatch colors and semantic income green, invited amber, restrained danger red while ensuring readable dark-theme contrast. Initials avatars dark slate with light text. If Appearance controls are present, select Dark instead of Light; if standalone authentication theme icon is a sun, change it to a moon. All text readable, no glow, no neon effects, no purple, no glassmorphism, no texture, no added decoration. Exact matched counterpart of input screen.

## Refinements and reference selection

- Light page boards used the approved light dashboard as the visual reference, except Registration, which used the finished light Sign in board.
- Dark page boards used their corresponding completed light board as the edit target.
- Registration and Page not found dark prompts explicitly required the complete page name in the board title.
- The final Categories light board used this targeted correction before the dark version was generated:

Use case: precise-object-edit. Edit this Pocketr Categories LIGHT concept. Make exactly one correction: in the MOBILE viewport remove the calendar/date selector labeled 'Sep 2026' from the row under the Pocketr header. Categories have no date filter. Expand the existing Personal selector to fill that row. Keep every other pixel, logo, text, color, layout, active navigation, desktop editor and card unchanged. Same canvas size, same frame coordinates, same LIGHT theme. No other changes.
