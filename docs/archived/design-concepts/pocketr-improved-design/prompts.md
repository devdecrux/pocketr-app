# Pocketr initial design concepts

Generated using the built-in image generation tool. Concept artwork only; no application code or dependencies changed.

## Light theme generation prompt

Use case: ui-mockup.
Create ONE polished high-fidelity product design concept image for Pocketr, a modern simple personal and household finance web app, LIGHT THEME, paired desktop and mobile responsive mockups. This is an actual app UI presentation, not a marketing website. Large landscape 2400 x 1500 composition, crisp legible typography and premium restrained visual design. Fill the board usefully, keep outer margins modest.

Composition: neutral pale gray presentation board. At top a small tasteful line "Pocketr / Light" and the exact motto "Simple on the surface. Solid underneath." Below show a large desktop app frame on the left taking about 72 percent width and a flat mobile app viewport on the right taking about 23 percent width, separated with generous space. Both top-aligned and bottom-aligned and completely visible, not overlapping. Realistic desktop aspect roughly 1.55:1 and mobile roughly 0.48:1. No device hardware, no perspective, no browser toolbar, no floating decorative props. Entire UI sharp, front-on. Mobile is not just a miniaturized desktop, it is a carefully reflowed single column interface.

Design language: Nuxt UI-inspired modern practical components, warm-white main background #FAFAFA, pure white sidebar and cards, light neutral #E5E7EB 1px borders, ink #172124 text, muted #67747C labels, restrained cyan #0891B2 brand/action accent from Pocketr logo, tiny amber #F59E0B accent only for logo and one chart marker. Buttons solid deep cyan with white text. Rounded rectangles about 10-12px, subtle shallow shadows only on popover, plentiful empty space, consistent 8-point spacing. Inter-like sans-serif. Large financial figures in tabular numerals. A calm usable finance tool, very few boxes, no gradients, no glassmorphism, no neon, no purple, no oversized pills. Thin consistent Lucide-style outline icons.

Brand icon accurately follows existing Pocketr identity: small flat bright cyan wallet body, slightly tilted amber banknote sticking out from the top, semicircular notch at the right edge and a dark teal circular fastener in that notch. Next to icon text exactly "Pocketr", with uppercase P and lowercase ocketr. No other logos.

Desktop:
- Fixed left sidebar about 230 CSS pixels wide, full viewport height. Top has wallet logo plus Pocketr wordmark horizontally, one subtle horizontal divider directly below.
- Under divider vertical nav rows with matching thin outline icons: "Dashboard" (grid icon, selected with pale cyan rectangular background), "Transactions" (arrows up/down), "Accounts" (wallet), "Categories" (shapes), "Household" (users). Generous row heights and padding. Household is included as the shared finance area. No invented budget, AI, investment, subscription or goal pages.
- At absolute bottom of sidebar, bottom divider then clickable profile row: circular initials avatar "AM", two lines "Alex Morgan" and "alex@example.com", chevrons at far right. This is fictional sample data.
- Show the profile popup OPEN just above this profile row within sidebar width. White bordered rounded popover with soft small shadow. Row "Settings" with gear icon. Divider. Label "Appearance" with inline three equal choices sun "Light", moon "Dark", monitor "System"; Light selected pale cyan. Divider. Row "Sign out" with logout outline icon. Clearly anchored to profile.
- Main header slim border bottom, left title "Dashboard", right context selector "Personal" with user outline icon and chevron, then small outlined date selector "September 2026".
- Main content has heading "Your money, at a glance" and muted subtitle "A little clarity for your everyday finances." On same row at right primary button "+ Add transaction".
- Two balanced summary cards: first "Available balance" large "€2,845.00", subtitle "Across 2 accounts"; second "Spent this month" large "€1,155.00", subtitle "September 2026". Small tasteful wallet and arrow icons. First balance visually stronger but no huge colored background.
- Under summary cards a wider left chart "Spending over time" with a tiny "Last 6 months" selector. Clean smooth cyan line, sparse horizontal guides, readable y-axis "€0", "€800", "€1,600"; x-axis "Apr", "May", "Jun", "Jul", "Aug", "Sep". Values plausible around 1320,1080,1460,980,1210,1155. Modest, visually quiet line chart, zero decorative gradient.
- Next to chart a narrow spending breakdown area titled "Top categories". Four labeled horizontal bars with right-aligned amounts: "Housing" "€650", "Groceries" "€285", "Transport" "€140", "Other" "€80". They add to €1,155. Bars proportional, mostly cyan and neutral with one muted amber.
- Below chart a clean "Recent expenses" list and small "View all" action. Three broad rows with pale icon tiles, merchant title, secondary category and date, right-aligned amounts: "Grocery market", "Groceries · Today", "−€46.80"; "City transport", "Transport · Today", "−€24.00"; "Coffee shop", "Other · Yesterday", "−€4.50". No dense spreadsheet chrome.

Mobile:
- Same exact light theme, same logo, same data. Top app bar with hamburger at far left, small wallet logo and "Pocketr", compact circular AM avatar at far right.
- Beneath, compact "Personal" context selector and "Sep 2026" period selector.
- Heading "Dashboard" and short muted "Your money, at a glance".
- Full-width balance card showing "Available balance", "€2,845.00", "Across 2 accounts"; smaller spending summary directly below "Spent this month" and "€1,155.00".
- Compact full-width "Spending over time" line chart with Apr–Sep labels and correct non-crowded spacing.
- "Recent expenses" section with the first two same expense rows, large comfortable touch targets.
- A persistent bottom action area with full-width deep cyan "+ Add transaction" button and safe bottom padding.
- Navigation is closed on this mobile screen; hamburger opens the same left slide-over sidebar represented in the desktop. Do not squeeze permanent desktop sidebar into mobile. No bottom nav.
Final constraints: single coherent design system across both layouts. Financial amounts and labels exactly as specified; all text readable. Prioritize restraint, alignment, real usability, mobile touch sizes. Only render the requested light-theme concept, no dark theme on this board.

## Dark theme edit prompt

Use case: style-transfer.
Edit the supplied Pocketr LIGHT theme UI concept into its matching DARK THEME version. The input image is the edit target, not an inspirational reference. Preserve the full canvas, exact desktop and mobile layouts, dimensions, all alignments, chart geometry, icons, wallet logo shape, wordmarks, component placement, all financial amounts and all text except changes explicitly specified below. Do not add, remove, resize, reorganize or crop any element. Keep both desktop sidebar and mobile viewport fully visible and the profile popover open. Preserve crisp legible typography.
Change ONLY theme colors and theme state:
- Change top board title "Pocketr / Light" to "Pocketr / Dark".
- Keep motto exactly "Simple on the surface. Solid underneath."
- Presentation board background becomes deep neutral charcoal #101214, with no texture.
- Desktop and mobile main canvas #15191D, sidebar #111518, cards #1C2227, popover #242C32.
- Subtle 1px borders #30383F. Near-white main text #EDF2F5, secondary text #A0ACB7. Good accessible contrast and visible separation of surfaces. No pitch black crushing.
- Brand primary accent bright cyan #22D3EE for chart and selected nav icon/text. Selected Dashboard row has a restrained dark cyan tinted background #11333E. All financial values near white.
- Primary "+ Add transaction" buttons fill #22D3EE with very dark #082F3A text and plus icon.
- Logo maintains existing bright cyan wallet and amber banknote. Its circular fastener becomes near white on the dark theme; no redesign.
- Summary icon backgrounds and expense icon tile backgrounds become subdued blue-gray #17313B with light cyan icons. AM initials avatar muted dark slate with near-white initials.
- Expense amounts near-white, no unnecessary red. Category bars restrained cyan, amber only on Other as before.
- Chart grid subtle gray #30383F. Cyan line crisp, no glow, no filled gradient.
- In OPEN profile Appearance chooser, change selected choice from "Light" to "Dark". The "Dark" tile has cyan-tinted background and light cyan moon/text, Light and System unselected on dark neutral surfaces. All three labels still visible.
- Dark theme popover may use a very subtle shadow. No neon, no glassmorphism, no glows, no purple, no new gradients or decorations.
Output one finished DARK theme concept image that is an exact paired counterpart of the supplied LIGHT image.
