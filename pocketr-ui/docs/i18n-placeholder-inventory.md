# I18n Placeholder Inventory

Vue i18n is configured in `src/i18n/index.ts`. Locale files live in `src/i18n/locales`.

Use `$t('key.path')` in templates and `const { t } = useI18n()` in `<script setup>` code. For strings produced in plain TypeScript helpers or Pinia stores, pass translated labels/messages in from the caller or move display formatting into composables/components.

## Frontend Views

- `src/views/DashboardPage.vue`
  - Lines 63, 170-171, 177, 188, 207, 218-219, 237, 248-249, 266, 277-278: `Uncategorized`, `Dashboard`, `Household overview`, `Your personal overview`, `Account Balances`, `No accounts yet.`, `Recent Transactions`, `No transactions yet.`, `Spending by Category`, `No spending data for this month.`, `Spending by Account`.
  - Lines 193, 257, 286: backend/data labels rendered directly: account type values, category names, account names.
- `src/views/TransactionsPage.vue`
  - Lines 190, 249, 253, 257, 269, 295, 318, 489, 491, 501, 512, 514, 525, 530, 534-535, 542, 546, 550, 554, 561, 564, 572, 576, 579, 583, 586, 589, 593, 601, 604, 612, 616, 619, 623, 626-627, 633-635, 638, 641, 649, 653, 656, 661, 664, 668, 671-672, 679, 682, 690, 694, 697, 702, 705, 709, 719, 729, 737, 739, 742, 747, 751, 759, 764, 778: all table headers, form titles, descriptions, labels, placeholders, notices, empty states, confirmation text, and fallback errors.
  - Lines 263, 797: transaction type labels and split side enum values currently render raw helper/backend enum values.
- `src/views/AccountsPage.vue`
  - Lines 96, 101, 119, 123, 136, 139, 222, 239, 264, 278, 283, 287-288, 295, 299, 303, 307, 313, 317, 320, 337, 345, 355-357, 363, 367, 370, 390, 394, 397, 417, 421, 424, 441, 449, 459-461, 470, 480, 483, 491, 494, 503, 511, 514, 519, 521, 525: table headers, shared label, rename description, create errors, page/dialog titles, account type tabs, form labels, placeholders, help text, actions, filters, loading/empty states.
  - Lines 113, 485: raw account type enum labels.
  - Lines 331, 381, 408, 435: currency names are backend response strings; decide whether to localize client-side by currency code or keep server-provided display names.
- `src/views/CategoriesPage.vue`
  - Lines 70, 74, 87, 99, 103, 117, 126, 132, 140, 156, 201, 206, 210-211, 214, 218, 221, 229, 238, 249, 257, 259, 262, 270: validation errors, duplicate errors, delete confirmation, table headers, page/dialog titles, labels, placeholders, empty/loading states, actions.
  - Lines 150, 218, 260: category names are user data and should not be translated.
- `src/views/SettingsPage.vue`
  - Lines 41, 64, 78, 96, 105, 119, 124, 126, 137, 146, 159, 164, 166, 178-179, 213, 221, 226-227, 238-240, 266, 275, 285, 289, 298, 308-310, 312, 317, 330: selected-file text, avatar and household success/error messages, card titles/descriptions, labels, buttons, pending invite text, create household copy.
  - Lines 250, 289: raw role/status labels should be mapped to i18n keys.
- `src/views/HouseholdSettingsPage.vue`
  - Lines 84, 102, 116-119, 124-126, 154-155, 158, 170, 185, 192-193, 196, 201, 205, 219-220, 228, 239, 248-250, 254-255, 271, 278, 293-294: unknown-user fallback, invite success/error messages, created/joined/shared date labels, titles, descriptions, field labels, placeholders, action labels, empty states.
  - Lines 176, 179, 259: raw household role/status/account type enum labels.
- `src/views/auth/LoginPage.vue`
  - Lines 58-59, 67, 71, 76, 80, 84-85, 89, 94, 96: login title/description, session-expired message, labels, placeholder, invalid-login error, submit states, registration prompt/link.
- `src/views/auth/RegistrationPage.vue`
  - Lines 16, 23, 27, 56-57, 63-67, 70, 75, 79, 82, 91, 96-97: default/error messages, title/description, labels, placeholders, submit states, login prompt/link.
- `src/views/NotFoundPage.vue`
  - Lines 7, 9, 11: not-found title, description, back link.

## Frontend Shared Components

- `src/components/Sidebar.vue`
  - Lines 49-54, 94-96, 133, 170, 174, 181: route labels, brand subtitle, household settings nav label, user menu labels.
- `src/components/ThemeMenu.vue` and `src/composables/useAppTheme.ts`
  - Lines `ThemeMenu.vue:16`, `useAppTheme.ts:4-6`, `useAppTheme.ts:11`: `Theme`, `Light`, `Dark`, `System`, theme preset label `Pocketr`.
- `src/components/ModeSwitcher.vue`
  - Lines 49, 55: `Select mode`, `Individual`.
- `src/components/AccountSelector.vue`
  - Lines 27, 72, 92: `Select account`, owner group label `Owner: {ownerId}`, account type group labels.
- `src/components/CategoryTagSelector.vue`
  - Lines 49, 55, 57: `Select category...`, `Search categories...`, `No categories found.`
- `src/components/DataTable.vue`
  - Lines 44, 139, 162-163: `No data.`, `Rows per page`, `Page {page} of {totalPages}`, `{totalElements} total`.
- `src/components/DateRangePicker.vue`
  - Lines 87, 159: `Date range`, `Clear date range`.
- `src/components/CategoryColorPicker.vue`
  - Line 27: `No color`.
- `src/components/app/AuthPageShell.vue`
  - Theme button aria label: `Theme`.
- `src/components/ui/**`
  - Accessibility strings to replace where present: `Close`, `Toggle Sidebar`, `Sidebar`, `Displays the mobile sidebar.`, `Command Palette`, `Search for a command to run...`.

## Frontend Utilities And Stores

- `src/utils/txnStrategies.ts`
  - Line 45: `Please fill in all required fields and enter a positive amount.`
- `src/utils/txnPresentation.ts`
  - Lines 11, 25, 31, 38, 44, 50: `Transfer`, `Expense`, `Income`, `Debt Payment`, `Opening Balance`, `Opening Debt`.
- `src/stores/account.ts`
  - Line 45: `Failed to load accounts.`
- `src/stores/category.ts`
  - Lines 23, 37, 56, 69: `Failed to load/create/rename/delete category.`
  - Lines 76-79: backend `message` response is surfaced directly; decide whether API returns message keys or frontend maps known messages.
- `src/stores/household.ts`
  - Lines 70, 97, 113, 128, 141, 156, 167, 179, 192: household fallback errors.
  - Lines 209-212: backend `message` response is surfaced directly.
- `src/stores/ledger.ts`
  - Line 37: `Failed to load transactions.`
- `src/views/*`
  - Several pages also parse `payload.message` from backend and display it directly: `TransactionsPage.vue:488-489,511-512`, `SettingsPage.vue:89-96,123-126,163-166`, `HouseholdSettingsPage.vue:123-126`.

## Backend API Error And Response Strings

These strings can reach clients through `ResponseStatusException` or through the custom exception handler, so they should become stable error codes/message keys if localized API responses are required.

- `CustomExceptionHandler.kt`
  - Lines 12-26: forwards exception messages for bad request, forbidden, not found, and unauthorized responses.
- `CustomAuthenticationEntryPoint.kt`, `CustomAuthenticationFailureHandler.kt`, `CustomAuthenticationSuccessHandler.kt`, `CustomLogoutSuccessHandler.kt`
  - Status-only responses today. If response bodies are added, use keys rather than hard-coded text.
- `CustomUserDetailsService.kt`
  - Line 19: `User with email {email} not found`.
- `ManageAccountImpl.kt`
  - Lines 40, 44, 51, 56, 61, 65, 92, 103, 110, 115, 118, 151, 153, 164-168: invalid account type/currency, opening balance validation, invalid mode, missing household id, membership/ownership errors, not-found errors, internal null messages.
- `OpeningBalanceServiceImpl.kt`
  - Lines 33-35, 39, 41, 43, 46, 49, 53, 70, 73, 82, 106, 111, 132: opening balance/debt validation, ownership/user errors, generated transaction descriptions `Opening balance - {account}`, `Opening debt - {account}`, system account name `Opening Equity`.
- `ManageCategoryImpl.kt`
  - Lines 27, 31, 44, 50, 63, 65, 67, 73, 81, 93, 95, 101, 108: duplicate category, category not found, ownership, in-use delete conflict, internal null messages.
- `ManageHouseholdImpl.kt`
  - Lines 44, 47, 67, 73, 96, 102, 113, 117, 125-126, 130, 134, 155, 159, 162, 167, 184, 187, 190, 193, 227, 233, 238, 240, 243, 262, 267, 269, 279, 290, 297, 330, 332, 362, 370-371, 381-383: household creation/invite/accept/leave/share/unshare validation, conflict, not-found, permission, and internal null messages.
- `ManageLedgerImpl.kt`
  - Lines 80, 93, 101, 126, 131, 172, 176, 202, 204, 248, 252, 257, 261, 264, 293, 298, 303, 306, 309, 336, 346, 611, 613, 675, 681-683: invalid currency, missing accounts/categories, category ownership, transaction/account not found, household membership/share/ownership errors, internal null messages.
  - Lines 635-641: response `txnKind` enum values `OPENING_BALANCE`, `OPENING_DEBT`, `DEBT_PAYMENT`, `TRANSFER`, `EXPENSE`, `INCOME`; frontend should map these to localized labels.
- `services/ledger/validations/*.kt`
  - `CrossUserAssetAccountTypeValidator.kt:25-26`, `DoubleEntryBalanceValidator.kt:14-15`, `HouseholdIdPresenceValidator.kt:9`, `HouseholdMembershipValidator.kt:16`, `HouseholdSharedAccountValidator.kt:19-20`, `IndividualModeOwnershipValidator.kt:14-15`, `MinimumSplitCountValidator.kt:11`, `PositiveSplitAmountValidator.kt:12`, `SplitSideValueValidator.kt:15`, `TransactionAccountCurrencyValidator.kt:15-16`.
- `GenerateReportImpl.kt`
  - Lines 45, 67, 70, 91, 106, 137, 140, 145, 148, 195, 215: report validation/permission errors and response category label `Debt Payment`.
- `UserAvatarService.kt`
  - Lines 41, 46, 54, 69, 72, 78, 84, 92, 102, 106, 119: avatar upload validation/error messages.

## Backend Data Strings Returned To The UI

- `CurrencySeeder.kt`
  - Currency display names `Euro`, `US Dollar`, `British Pound`, `Japanese Yen`, `Swiss Franc`, `Bahraini Dinar`, `Canadian Dollar`, `Australian Dollar`, `Swedish Krona`, `Norwegian Krone`, `Danish Krone`, `Polish Zloty`, `Czech Koruna`, `Hungarian Forint`, `Romanian Leu`.
- `DevelopmentDatabaseSeeder.kt`
  - Dev/demo user names, household names, account names, category names, and transaction descriptions are returned by APIs in dev mode. These are seeded sample data, not UI chrome; localize only if demo content must change with locale.
- Backend enum response values shown in UI unless mapped:
  - Account types: `ASSET`, `LIABILITY`, `INCOME`, `EXPENSE`, `EQUITY`.
  - Split sides: `DEBIT`, `CREDIT`.
  - Household roles/statuses: `OWNER`, `ADMIN`, `MEMBER`, `ACTIVE`, `INVITED`.
  - Transaction kinds: `EXPENSE`, `INCOME`, `TRANSFER`, `DEBT_PAYMENT`, `OPENING_BALANCE`, `OPENING_DEBT`.

## Recommended Replacement Strategy

- Translate UI chrome in Vue with namespaced keys, for example `transactions.create.title`.
- Do not translate user-entered data such as account names, category names, transaction descriptions, household names, or emails.
- Map backend enums to frontend i18n labels before rendering.
- For backend errors, prefer returning stable machine-readable error codes and parameters, then translate on the frontend. Avoid relying on localized free-text backend messages for UI display.
- For generated backend data like opening-balance transaction descriptions, either store stable kind/metadata and format in the frontend, or accept that historical stored descriptions remain in the language used at creation time.
