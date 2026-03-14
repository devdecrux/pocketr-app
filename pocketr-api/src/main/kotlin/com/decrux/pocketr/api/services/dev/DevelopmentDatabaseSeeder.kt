package com.decrux.pocketr.api.services.dev

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.db.auth.UserRole
import com.decrux.pocketr.api.entities.db.household.Household
import com.decrux.pocketr.api.entities.db.household.HouseholdAccountShare
import com.decrux.pocketr.api.entities.db.household.HouseholdMember
import com.decrux.pocketr.api.entities.db.household.HouseholdRole
import com.decrux.pocketr.api.entities.db.household.MemberStatus
import com.decrux.pocketr.api.entities.db.ledger.Account
import com.decrux.pocketr.api.entities.db.ledger.AccountType
import com.decrux.pocketr.api.entities.db.ledger.CategoryTag
import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.entities.db.ledger.LedgerSplit
import com.decrux.pocketr.api.entities.db.ledger.LedgerTxn
import com.decrux.pocketr.api.entities.db.ledger.SplitSide
import com.decrux.pocketr.api.repositories.AccountCurrentBalanceRepository
import com.decrux.pocketr.api.repositories.AccountRepository
import com.decrux.pocketr.api.repositories.CategoryTagRepository
import com.decrux.pocketr.api.repositories.CurrencyRepository
import com.decrux.pocketr.api.repositories.HouseholdAccountShareRepository
import com.decrux.pocketr.api.repositories.HouseholdMemberRepository
import com.decrux.pocketr.api.repositories.HouseholdRepository
import com.decrux.pocketr.api.repositories.LedgerTxnRepository
import com.decrux.pocketr.api.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Component
@Profile("dev")
@Order(1)
class DevelopmentDatabaseSeeder(
    private val userRepository: UserRepository,
    private val currencyRepository: CurrencyRepository,
    private val accountRepository: AccountRepository,
    private val categoryTagRepository: CategoryTagRepository,
    private val householdRepository: HouseholdRepository,
    private val householdMemberRepository: HouseholdMemberRepository,
    private val householdAccountShareRepository: HouseholdAccountShareRepository,
    private val ledgerTxnRepository: LedgerTxnRepository,
    private val accountCurrentBalanceRepository: AccountCurrentBalanceRepository,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        if (!isFreshDomainDatabase()) {
            logger.info("development_seed skipped=true reason=domain_tables_not_empty")
            return
        }

        val eur =
            currencyRepository
                .findById(CURRENCY_CODE)
                .orElseThrow {
                    IllegalStateException("Currency '$CURRENCY_CODE' must exist before development seed data is created")
                }

        logger.info("development_seed started=true user_count={}", USER_SEEDS.size)

        val usersByEmail = persistUsers()
        val categoriesByUserId = persistCategories(usersByEmail)
        val accountsByUserId = persistAccounts(usersByEmail, eur)
        val contexts =
            USER_SEEDS.map { seed ->
                val user = usersByEmail.getValue(seed.email)
                val userId = requireNotNull(user.userId) { "User ID must not be null" }
                SeedContext(
                    seed = seed,
                    user = user,
                    accounts = accountsByUserId.getValue(userId),
                    categories = categoriesByUserId.getValue(userId),
                )
            }

        createHouseholdAndShares(contexts)

        val rawBalancesByAccountId = linkedMapOf<UUID, Long>()
        val transactions = contexts.flatMap { buildTransactions(it, eur, rawBalancesByAccountId) }
        ledgerTxnRepository.saveAllAndFlush(transactions)

        val currentBalances =
            rawBalancesByAccountId
                .filterValues { it != 0L }
                .onEach { (accountId, rawBalanceMinor) ->
                    accountCurrentBalanceRepository.addDelta(accountId, rawBalanceMinor)
                }
                .size

        logger.info(
            "development_seed completed=true user_count={} transaction_count={} account_count={} snapshot_count={}",
            contexts.size,
            transactions.size,
            rawBalancesByAccountId.size,
            currentBalances,
        )
    }

    private fun isFreshDomainDatabase(): Boolean =
        userRepository.count() == 0L &&
            accountRepository.count() == 0L &&
            categoryTagRepository.count() == 0L &&
            householdRepository.count() == 0L &&
            householdMemberRepository.count() == 0L &&
            householdAccountShareRepository.count() == 0L &&
            ledgerTxnRepository.count() == 0L &&
            accountCurrentBalanceRepository.count() == 0L

    private fun persistUsers(): Map<String, User> {
        val encodedSeedPassword =
            requireNotNull(passwordEncoder.encode(SEED_PASSWORD)) {
                "Password encoder returned null"
            }
        val users =
            userRepository
                .saveAllAndFlush(
                    USER_SEEDS.map { seed ->
                        User(
                            password = encodedSeedPassword,
                            email = seed.email,
                            firstName = seed.firstName,
                            lastName = seed.lastName,
                            roles = mutableListOf(UserRole(role = ROLE_USER)),
                        )
                    },
                ).associateBy { it.email }

        return users
    }

    private fun persistCategories(usersByEmail: Map<String, User>): Map<Long, Map<String, CategoryTag>> {
        val categories =
            categoryTagRepository
                .saveAllAndFlush(
                    USER_SEEDS.flatMap { seed ->
                        val user = usersByEmail.getValue(seed.email)
                        CATEGORY_SEEDS.map { categorySeed ->
                            CategoryTag(
                                owner = user,
                                name = categorySeed.name,
                                color = categorySeed.color,
                            )
                        }
                    },
                )

        return categories
            .groupBy { requireNotNull(it.owner?.userId) { "Category owner ID must not be null" } }
            .mapValues { (_, ownedCategories) -> ownedCategories.associateBy { it.name } }
    }

    private fun persistAccounts(
        usersByEmail: Map<String, User>,
        currency: Currency,
    ): Map<Long, SeedAccounts> {
        val accounts =
            accountRepository
                .saveAllAndFlush(
                    USER_SEEDS.flatMap { seed ->
                        val user = usersByEmail.getValue(seed.email)
                        listOf(
                            Account(
                                owner = user,
                                name = PRIMARY_CHECKING_NAME,
                                type = AccountType.ASSET,
                                currency = currency,
                            ),
                            Account(
                                owner = user,
                                name = DAILY_CHECKING_NAME,
                                type = AccountType.ASSET,
                                currency = currency,
                            ),
                            Account(
                                owner = user,
                                name = SAVINGS_NAME,
                                type = AccountType.ASSET,
                                currency = currency,
                            ),
                            Account(
                                owner = user,
                                name = EXPENSE_ACCOUNT_NAME,
                                type = AccountType.EXPENSE,
                                currency = currency,
                            ),
                            Account(
                                owner = user,
                                name = INCOME_ACCOUNT_NAME,
                                type = AccountType.INCOME,
                                currency = currency,
                            ),
                            Account(
                                owner = user,
                                name = EQUITY_ACCOUNT_NAME,
                                type = AccountType.EQUITY,
                                currency = currency,
                            ),
                            Account(
                                owner = user,
                                name = seed.liabilityName,
                                type = AccountType.LIABILITY,
                                currency = currency,
                            ),
                        )
                    },
                )

        return accounts
            .groupBy { requireNotNull(it.owner?.userId) { "Account owner ID must not be null" } }
            .mapValues { (_, userAccounts) ->
                val byName = userAccounts.associateBy { it.name }
                SeedAccounts(
                    primaryChecking = byName.getValue(PRIMARY_CHECKING_NAME),
                    dailyChecking = byName.getValue(DAILY_CHECKING_NAME),
                    savings = byName.getValue(SAVINGS_NAME),
                    expense = byName.getValue(EXPENSE_ACCOUNT_NAME),
                    income = byName.getValue(INCOME_ACCOUNT_NAME),
                    equity = byName.getValue(EQUITY_ACCOUNT_NAME),
                    liability = byName.getValue(
                        userAccounts
                            .first { it.type == AccountType.LIABILITY }
                            .name,
                    ),
                    all = userAccounts,
                )
            }
    }

    private fun createHouseholdAndShares(contexts: List<SeedContext>) {
        val owner = contexts[0]
        val admin = contexts[1]
        val member = contexts[2]
        val invitedAt = Instant.parse("2026-01-12T09:00:00Z")
        val joinedAt = invitedAt.plusSeconds(86_400)

        val household =
            householdRepository.saveAndFlush(
                Household(
                    name = "Demo Household",
                    createdBy = owner.user,
                    createdAt = Instant.parse("2026-01-10T09:00:00Z"),
                    members =
                        mutableListOf(
                            HouseholdMember(
                                user = owner.user,
                                role = HouseholdRole.OWNER,
                                status = MemberStatus.ACTIVE,
                                joinedAt = Instant.parse("2026-01-10T09:00:00Z"),
                            ),
                            HouseholdMember(
                                user = admin.user,
                                role = HouseholdRole.ADMIN,
                                status = MemberStatus.ACTIVE,
                                invitedBy = owner.user,
                                invitedAt = invitedAt,
                                joinedAt = joinedAt,
                            ),
                            HouseholdMember(
                                user = member.user,
                                role = HouseholdRole.MEMBER,
                                status = MemberStatus.ACTIVE,
                                invitedBy = owner.user,
                                invitedAt = invitedAt.plusSeconds(3_600),
                                joinedAt = joinedAt.plusSeconds(3_600),
                            ),
                        ),
                ).apply {
                    members.forEach { it.household = this }
                },
            )

        householdAccountShareRepository.saveAllAndFlush(
            listOf(owner, admin, member).map { context ->
                HouseholdAccountShare(
                    household = household,
                    account = context.accounts.primaryChecking,
                    sharedBy = context.user,
                )
            },
        )
    }

    private fun buildTransactions(
        context: SeedContext,
        currency: Currency,
        rawBalancesByAccountId: MutableMap<UUID, Long>,
    ): List<LedgerTxn> {
        val transactions = mutableListOf<LedgerTxn>()
        val baseDate = LocalDate.of(2026, 1, 1).plusDays((context.seed.ordinal - 1L) * 4L)

        val assetAccounts =
            listOf(
                context.accounts.primaryChecking,
                context.accounts.dailyChecking,
                context.accounts.savings,
            )

        context.seed.openingBalances.forEachIndexed { index, amountMinor ->
            transactions +=
                buildTransaction(
                    creator = context.user,
                    currency = currency,
                    txnDate = baseDate.plusDays(index.toLong()),
                    description = "Opening balance - ${assetAccounts[index].name}",
                    debitAccount = assetAccounts[index],
                    creditAccount = context.accounts.equity,
                    amountMinor = amountMinor,
                    categoryTag = context.categories.getValue(CATEGORY_OPENING_BALANCE),
                    categoryPlacement = CategoryPlacement.DEBIT,
                    rawBalancesByAccountId = rawBalancesByAccountId,
                )
        }

        val transferCategory = context.categories.getValue(CATEGORY_INTERNAL_TRANSFER)
        transactions +=
            buildTransaction(
                creator = context.user,
                currency = currency,
                txnDate = baseDate.plusDays(3),
                description = "Funding move to ${context.accounts.dailyChecking.name}",
                debitAccount = context.accounts.dailyChecking,
                creditAccount = context.accounts.primaryChecking,
                amountMinor = 12_000L + (context.seed.ordinal * 350L),
                categoryTag = transferCategory,
                categoryPlacement = CategoryPlacement.DEBIT,
                rawBalancesByAccountId = rawBalancesByAccountId,
            )
        transactions +=
            buildTransaction(
                creator = context.user,
                currency = currency,
                txnDate = baseDate.plusDays(4),
                description = "Savings contribution from ${context.accounts.savings.name}",
                debitAccount = context.accounts.primaryChecking,
                creditAccount = context.accounts.savings,
                amountMinor = 18_500L + (context.seed.ordinal * 425L),
                categoryTag = transferCategory,
                categoryPlacement = CategoryPlacement.DEBIT,
                rawBalancesByAccountId = rawBalancesByAccountId,
            )
        transactions +=
            buildTransaction(
                creator = context.user,
                currency = currency,
                txnDate = baseDate.plusDays(5),
                description = "Reserve top-up from ${context.accounts.dailyChecking.name}",
                debitAccount = context.accounts.savings,
                creditAccount = context.accounts.dailyChecking,
                amountMinor = 9_500L + (context.seed.ordinal * 275L),
                categoryTag = transferCategory,
                categoryPlacement = CategoryPlacement.DEBIT,
                rawBalancesByAccountId = rawBalancesByAccountId,
            )

        val expenseCategoryCycle =
            listOf(
                CATEGORY_GROCERIES,
                CATEGORY_DINING_OUT,
                CATEGORY_UTILITIES,
                CATEGORY_TRANSPORT,
                CATEGORY_ENTERTAINMENT,
                CATEGORY_HEALTH,
            )
        val expenseBaseAmounts = listOf(4_900L, 2_700L, 8_100L, 3_400L, 5_600L)

        assetAccounts.forEachIndexed { assetIndex, assetAccount ->
            repeat(EXPENSES_PER_ASSET_ACCOUNT) { expenseIndex ->
                val categoryName = expenseCategoryCycle[(assetIndex + expenseIndex) % expenseCategoryCycle.size]
                val amountMinor =
                    expenseBaseAmounts[expenseIndex] +
                        (assetIndex * 180L) +
                        (context.seed.ordinal * 95L)

                transactions +=
                    buildTransaction(
                        creator = context.user,
                        currency = currency,
                        txnDate = baseDate.plusDays(6 + assetIndex * 8L + expenseIndex.toLong()),
                        description = "${categoryName.lowercase().replaceFirstChar { it.uppercase() }} from ${assetAccount.name}",
                        debitAccount = context.accounts.expense,
                        creditAccount = assetAccount,
                        amountMinor = amountMinor,
                        categoryTag = context.categories.getValue(categoryName),
                        categoryPlacement = CategoryPlacement.DEBIT,
                        rawBalancesByAccountId = rawBalancesByAccountId,
                    )
            }

            repeat(INCOMES_PER_ASSET_ACCOUNT) { incomeIndex ->
                val amountMinor =
                    if (incomeIndex == 0) {
                        48_000L + (assetIndex * 2_500L) + (context.seed.ordinal * 1_050L)
                    } else {
                        11_500L + (assetIndex * 900L) + (context.seed.ordinal * 450L)
                    }

                transactions +=
                    buildTransaction(
                        creator = context.user,
                        currency = currency,
                        txnDate = baseDate.plusDays(30 + assetIndex * 3L + incomeIndex.toLong()),
                        description = if (incomeIndex == 0) "Salary into ${assetAccount.name}" else "Extra income into ${assetAccount.name}",
                        debitAccount = assetAccount,
                        creditAccount = context.accounts.income,
                        amountMinor = amountMinor,
                        categoryTag = context.categories.getValue(CATEGORY_SALARY),
                        categoryPlacement = CategoryPlacement.CREDIT,
                        rawBalancesByAccountId = rawBalancesByAccountId,
                    )
            }
        }

        transactions +=
            buildTransaction(
                creator = context.user,
                currency = currency,
                txnDate = baseDate.plusDays(42),
                description = "Opening balance - ${context.accounts.liability.name}",
                debitAccount = context.accounts.equity,
                creditAccount = context.accounts.liability,
                amountMinor = context.seed.liabilityOpeningBalance,
                categoryTag = context.categories.getValue(CATEGORY_OPENING_BALANCE),
                categoryPlacement = CategoryPlacement.CREDIT,
                rawBalancesByAccountId = rawBalancesByAccountId,
            )

        context.seed.liabilityRepayments.forEachIndexed { index, amountMinor ->
            transactions +=
                buildTransaction(
                    creator = context.user,
                    currency = currency,
                    txnDate = baseDate.plusDays(43 + index.toLong()),
                    description = "Repayment ${index + 1} for ${context.accounts.liability.name}",
                    debitAccount = context.accounts.liability,
                    creditAccount = context.accounts.primaryChecking,
                    amountMinor = amountMinor,
                    categoryTag = context.categories.getValue(CATEGORY_DEBT_PAYMENT),
                    categoryPlacement = CategoryPlacement.DEBIT,
                    rawBalancesByAccountId = rawBalancesByAccountId,
                )
        }

        return transactions
    }

    private fun buildTransaction(
        creator: User,
        currency: Currency,
        txnDate: LocalDate,
        description: String,
        debitAccount: Account,
        creditAccount: Account,
        amountMinor: Long,
        categoryTag: CategoryTag,
        categoryPlacement: CategoryPlacement,
        rawBalancesByAccountId: MutableMap<UUID, Long>,
    ): LedgerTxn {
        val txn =
            LedgerTxn(
                createdBy = creator,
                txnDate = txnDate,
                description = description,
                currency = currency,
            )

        val debitSplit =
            LedgerSplit(
                transaction = txn,
                account = debitAccount,
                side = SplitSide.DEBIT,
                amountMinor = amountMinor,
                categoryTag = if (categoryPlacement == CategoryPlacement.DEBIT) categoryTag else null,
            )
        val creditSplit =
            LedgerSplit(
                transaction = txn,
                account = creditAccount,
                side = SplitSide.CREDIT,
                amountMinor = amountMinor,
                categoryTag = if (categoryPlacement == CategoryPlacement.CREDIT) categoryTag else null,
            )

        txn.splits = mutableListOf(debitSplit, creditSplit)

        addRawBalance(rawBalancesByAccountId, debitAccount, amountMinor)
        addRawBalance(rawBalancesByAccountId, creditAccount, -amountMinor)

        return txn
    }

    private fun addRawBalance(
        rawBalancesByAccountId: MutableMap<UUID, Long>,
        account: Account,
        delta: Long,
    ) {
        val accountId = requireNotNull(account.id) { "Account ID must not be null" }
        rawBalancesByAccountId[accountId] = (rawBalancesByAccountId[accountId] ?: 0L) + delta
    }

    private data class SeedContext(
        val seed: UserSeed,
        val user: User,
        val accounts: SeedAccounts,
        val categories: Map<String, CategoryTag>,
    )

    private data class SeedAccounts(
        val primaryChecking: Account,
        val dailyChecking: Account,
        val savings: Account,
        val expense: Account,
        val income: Account,
        val equity: Account,
        val liability: Account,
        val all: List<Account>,
    )

    private data class CategorySeed(
        val name: String,
        val color: String,
    )

    private data class UserSeed(
        val ordinal: Long,
        val nickname: String,
        val suffix: Int,
        val firstName: String,
        val lastName: String,
        val liabilityName: String,
        val openingBalances: List<Long>,
        val liabilityOpeningBalance: Long,
        val liabilityRepayments: List<Long>,
    ) {
        val email: String
            get() = "$nickname.${suffix.toString().padStart(3, '0')}@test.com"
    }

    private enum class CategoryPlacement {
        DEBIT,
        CREDIT,
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(DevelopmentDatabaseSeeder::class.java)

        private const val ROLE_USER = "USER"
        private const val SEED_PASSWORD = "test123"
        private const val CURRENCY_CODE = "EUR"

        private const val PRIMARY_CHECKING_NAME = "Primary Checking"
        private const val DAILY_CHECKING_NAME = "Daily Checking"
        private const val SAVINGS_NAME = "Rainy Day Savings"
        private const val EXPENSE_ACCOUNT_NAME = "General Expenses"
        private const val INCOME_ACCOUNT_NAME = "Income Clearing"
        private const val EQUITY_ACCOUNT_NAME = "Opening Equity"

        private const val CATEGORY_OPENING_BALANCE = "Opening Balance"
        private const val CATEGORY_INTERNAL_TRANSFER = "Internal Transfer"
        private const val CATEGORY_GROCERIES = "Groceries"
        private const val CATEGORY_DINING_OUT = "Dining Out"
        private const val CATEGORY_UTILITIES = "Utilities"
        private const val CATEGORY_TRANSPORT = "Transport"
        private const val CATEGORY_ENTERTAINMENT = "Entertainment"
        private const val CATEGORY_HEALTH = "Health"
        private const val CATEGORY_SALARY = "Salary"
        private const val CATEGORY_DEBT_PAYMENT = "Debt Payment"

        private const val EXPENSES_PER_ASSET_ACCOUNT = 5
        private const val INCOMES_PER_ASSET_ACCOUNT = 2

        private val CATEGORY_SEEDS =
            listOf(
                CategorySeed(CATEGORY_OPENING_BALANCE, "#1D4ED8"),
                CategorySeed(CATEGORY_INTERNAL_TRANSFER, "#4F46E5"),
                CategorySeed(CATEGORY_GROCERIES, "#16A34A"),
                CategorySeed(CATEGORY_DINING_OUT, "#EA580C"),
                CategorySeed(CATEGORY_UTILITIES, "#0F766E"),
                CategorySeed(CATEGORY_TRANSPORT, "#2563EB"),
                CategorySeed(CATEGORY_ENTERTAINMENT, "#7C3AED"),
                CategorySeed(CATEGORY_HEALTH, "#DC2626"),
                CategorySeed(CATEGORY_SALARY, "#059669"),
                CategorySeed(CATEGORY_DEBT_PAYMENT, "#92400E"),
            )

        private val USER_SEEDS =
            listOf(
                UserSeed(
                    ordinal = 1,
                    nickname = "atlas",
                    suffix = 101,
                    firstName = "Atlas",
                    lastName = "Hayes",
                    liabilityName = "Apartment Mortgage",
                    openingBalances = listOf(320_000L, 185_000L, 910_000L),
                    liabilityOpeningBalance = 18_500_000L,
                    liabilityRepayments = listOf(125_000L, 128_000L),
                ),
                UserSeed(
                    ordinal = 2,
                    nickname = "blaze",
                    suffix = 102,
                    firstName = "Blake",
                    lastName = "Morrison",
                    liabilityName = "Travel Credit Card",
                    openingBalances = listOf(295_000L, 210_000L, 860_000L),
                    liabilityOpeningBalance = 420_000L,
                    liabilityRepayments = listOf(48_000L, 52_000L),
                ),
                UserSeed(
                    ordinal = 3,
                    nickname = "cedar",
                    suffix = 103,
                    firstName = "Cedar",
                    lastName = "Brooks",
                    liabilityName = "Car Lease",
                    openingBalances = listOf(340_000L, 198_000L, 945_000L),
                    liabilityOpeningBalance = 2_480_000L,
                    liabilityRepayments = listOf(86_000L, 86_000L),
                ),
                UserSeed(
                    ordinal = 4,
                    nickname = "delta",
                    suffix = 104,
                    firstName = "Delia",
                    lastName = "Frost",
                    liabilityName = "Student Loan",
                    openingBalances = listOf(305_000L, 176_000L, 790_000L),
                    liabilityOpeningBalance = 3_200_000L,
                    liabilityRepayments = listOf(64_000L, 64_000L),
                ),
                UserSeed(
                    ordinal = 5,
                    nickname = "ember",
                    suffix = 105,
                    firstName = "Ember",
                    lastName = "Lane",
                    liabilityName = "Personal Loan",
                    openingBalances = listOf(330_000L, 201_000L, 875_000L),
                    liabilityOpeningBalance = 1_150_000L,
                    liabilityRepayments = listOf(57_500L, 57_500L),
                ),
                UserSeed(
                    ordinal = 6,
                    nickname = "frost",
                    suffix = 106,
                    firstName = "Freya",
                    lastName = "Cole",
                    liabilityName = "Home Renovation Loan",
                    openingBalances = listOf(360_000L, 224_000L, 980_000L),
                    liabilityOpeningBalance = 1_780_000L,
                    liabilityRepayments = listOf(74_000L, 76_000L),
                ),
                UserSeed(
                    ordinal = 7,
                    nickname = "grove",
                    suffix = 107,
                    firstName = "Graham",
                    lastName = "Pierce",
                    liabilityName = "Motorcycle Finance",
                    openingBalances = listOf(315_000L, 189_000L, 845_000L),
                    liabilityOpeningBalance = 960_000L,
                    liabilityRepayments = listOf(39_000L, 41_000L),
                ),
                UserSeed(
                    ordinal = 8,
                    nickname = "harbor",
                    suffix = 108,
                    firstName = "Harper",
                    lastName = "Quinn",
                    liabilityName = "Medical Loan",
                    openingBalances = listOf(345_000L, 214_000L, 935_000L),
                    liabilityOpeningBalance = 680_000L,
                    liabilityRepayments = listOf(28_000L, 32_000L),
                ),
            )
    }
}
