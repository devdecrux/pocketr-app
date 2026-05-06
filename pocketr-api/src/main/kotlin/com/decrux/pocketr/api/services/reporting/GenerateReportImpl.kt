package com.decrux.pocketr.api.services.reporting

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.db.ledger.AccountType
import com.decrux.pocketr.api.entities.db.ledger.SplitSide
import com.decrux.pocketr.api.entities.dtos.AccountBalanceSummaryDto
import com.decrux.pocketr.api.entities.dtos.AccountBalanceTimeseriesDto
import com.decrux.pocketr.api.entities.dtos.BalanceTimeseriesPointDto
import com.decrux.pocketr.api.entities.dtos.MonthlyExpenseDto
import com.decrux.pocketr.api.entities.dtos.RolloverExpenseReportDto
import com.decrux.pocketr.api.exceptions.BadRequestException
import com.decrux.pocketr.api.exceptions.ForbiddenException
import com.decrux.pocketr.api.exceptions.NotFoundException
import com.decrux.pocketr.api.repositories.AccountRepository
import com.decrux.pocketr.api.repositories.LedgerSplitRepository
import com.decrux.pocketr.api.repositories.projections.LiabilityPaymentProjection
import com.decrux.pocketr.api.repositories.projections.MonthlyExpenseProjection
import com.decrux.pocketr.api.services.household.ManageHousehold
import com.decrux.pocketr.api.services.rollover.RolloverPeriod
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Service
class GenerateReportImpl(
    private val ledgerSplitRepository: LedgerSplitRepository,
    private val accountRepository: AccountRepository,
    private val manageHousehold: ManageHousehold,
) : GenerateReport {
    @Transactional(readOnly = true)
    override fun getMonthlyExpenses(
        user: User,
        period: YearMonth,
        mode: String,
        householdId: UUID?,
    ): List<MonthlyExpenseDto> = getRolloverExpenses(user, period, mode, householdId).entries

    @Transactional(readOnly = true)
    override fun getRolloverExpenses(
        user: User,
        period: YearMonth,
        mode: String,
        householdId: UUID?,
    ): RolloverExpenseReportDto {
        val expenseRows: List<MonthlyExpenseProjection>
        val liabilityPaymentRows: List<LiabilityPaymentProjection>
        val rolloverDay: Int

        when (mode.uppercase()) {
            MODE_INDIVIDUAL -> {
                val userId = requireNotNull(user.userId) { "User ID must not be null" }
                rolloverDay = user.rolloverDay
                val rolloverPeriod = RolloverPeriod.startingIn(period, rolloverDay)
                expenseRows =
                    ledgerSplitRepository.monthlyExpensesByUser(
                        userId,
                        rolloverPeriod.startInclusive,
                        rolloverPeriod.endExclusive,
                        SplitSide.DEBIT,
                        SplitSide.CREDIT,
                    )
                liabilityPaymentRows =
                    ledgerSplitRepository.monthlyLiabilityPaymentsByUser(
                        userId,
                        rolloverPeriod.startInclusive,
                        rolloverPeriod.endExclusive,
                        SplitSide.DEBIT,
                        SplitSide.CREDIT,
                    )
            }

            MODE_HOUSEHOLD -> {
                val hId =
                    householdId
                        ?: throw BadRequestException("householdId is required for household mode")
                val userId = requireNotNull(user.userId) { "User ID must not be null" }
                if (!manageHousehold.isActiveMember(hId, userId)) {
                    throw ForbiddenException("Not an active member of this household")
                }
                rolloverDay = manageHousehold.getRolloverDay(hId)
                val rolloverPeriod = RolloverPeriod.startingIn(period, rolloverDay)
                expenseRows =
                    ledgerSplitRepository.monthlyExpensesByHousehold(
                        hId,
                        rolloverPeriod.startInclusive,
                        rolloverPeriod.endExclusive,
                        SplitSide.DEBIT,
                        SplitSide.CREDIT,
                    )
                liabilityPaymentRows =
                    ledgerSplitRepository.monthlyLiabilityPaymentsByHousehold(
                        hId,
                        rolloverPeriod.startInclusive,
                        rolloverPeriod.endExclusive,
                        SplitSide.DEBIT,
                        SplitSide.CREDIT,
                    )
            }

            else -> {
                throw BadRequestException("Invalid mode: $mode. Must be INDIVIDUAL or HOUSEHOLD")
            }
        }

        val rolloverPeriod = RolloverPeriod.startingIn(period, rolloverDay)
        return RolloverExpenseReportDto(
            periodStart = rolloverPeriod.startInclusive,
            periodEnd = rolloverPeriod.endExclusive.minusDays(1),
            entries =
                buildList {
                    addAll(expenseRows.map { it.toDto() })
                    addAll(liabilityPaymentRows.map { it.toDebtPaymentDto() })
                },
        )
    }

    @Transactional(readOnly = true)
    override fun getLifetimeExpenses(
        user: User,
        mode: String,
        householdId: UUID?,
    ): List<MonthlyExpenseDto> {
        val expenseRows: List<MonthlyExpenseProjection>
        val liabilityPaymentRows: List<LiabilityPaymentProjection>

        when (mode.uppercase()) {
            MODE_INDIVIDUAL -> {
                val userId = requireNotNull(user.userId) { "User ID must not be null" }
                expenseRows =
                    ledgerSplitRepository.lifetimeExpensesByUser(
                        userId,
                        SplitSide.DEBIT,
                        SplitSide.CREDIT,
                    )
                liabilityPaymentRows =
                    ledgerSplitRepository.lifetimeLiabilityPaymentsByUser(
                        userId,
                        SplitSide.DEBIT,
                        SplitSide.CREDIT,
                    )
            }

            MODE_HOUSEHOLD -> {
                val hId =
                    householdId
                        ?: throw BadRequestException("householdId is required for household mode")
                val userId = requireNotNull(user.userId) { "User ID must not be null" }
                if (!manageHousehold.isActiveMember(hId, userId)) {
                    throw ForbiddenException("Not an active member of this household")
                }
                expenseRows =
                    ledgerSplitRepository.lifetimeExpensesByHousehold(
                        hId,
                        SplitSide.DEBIT,
                        SplitSide.CREDIT,
                    )
                liabilityPaymentRows =
                    ledgerSplitRepository.lifetimeLiabilityPaymentsByHousehold(
                        hId,
                        SplitSide.DEBIT,
                        SplitSide.CREDIT,
                    )
            }

            else -> {
                throw BadRequestException("Invalid mode: $mode. Must be INDIVIDUAL or HOUSEHOLD")
            }
        }

        return buildList {
            addAll(expenseRows.map { it.toDto() })
            addAll(liabilityPaymentRows.map { it.toDebtPaymentDto() })
        }
    }

    @Transactional(readOnly = true)
    override fun getAllAccountBalances(
        user: User,
        asOf: LocalDate,
    ): List<AccountBalanceSummaryDto> {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }
        val accounts = accountRepository.findByOwnerUserId(userId)

        return accounts.map { account ->
            val accountId = requireNotNull(account.id)
            val isDebitNormal = account.type in DEBIT_NORMAL_TYPES
            val balanceMinor =
                if (account.type == AccountType.EXPENSE) {
                    val rolloverPeriod = RolloverPeriod.containing(asOf, user.rolloverDay)
                    ledgerSplitRepository.computeBalanceBetween(
                        accountId,
                        rolloverPeriod.startInclusive,
                        asOf,
                        SplitSide.DEBIT,
                        SplitSide.CREDIT,
                    )
                } else if (isDebitNormal) {
                    ledgerSplitRepository.computeBalance(
                        accountId,
                        asOf,
                        SplitSide.DEBIT,
                        SplitSide.CREDIT,
                    )
                } else {
                    ledgerSplitRepository.computeBalance(accountId, asOf, SplitSide.CREDIT, SplitSide.DEBIT)
                }

            AccountBalanceSummaryDto(
                accountId = accountId,
                accountName = account.name,
                accountType = account.type.name,
                currency = requireNotNull(account.currency?.code),
                balanceMinor = balanceMinor,
            )
        }
    }

    @Transactional(readOnly = true)
    override fun getBalanceTimeseries(
        accountId: UUID,
        dateFrom: LocalDate,
        dateTo: LocalDate,
        user: User,
    ): AccountBalanceTimeseriesDto {
        if (dateFrom.isAfter(dateTo)) {
            throw BadRequestException("dateFrom must be before or equal to dateTo")
        }

        val userId = requireNotNull(user.userId) { "User ID must not be null" }

        val account =
            accountRepository
                .findById(accountId)
                .orElseThrow { NotFoundException("Account not found") }

        if (account.owner?.userId != userId) {
            throw ForbiddenException("Not the owner of this account")
        }

        val isDebitNormal = account.type in DEBIT_NORMAL_TYPES
        val positive = if (isDebitNormal) SplitSide.DEBIT else SplitSide.CREDIT
        val negative = if (isDebitNormal) SplitSide.CREDIT else SplitSide.DEBIT

        // Get opening balance (everything before dateFrom)
        val openingBalance =
            ledgerSplitRepository.computeBalance(
                accountId,
                dateFrom.minusDays(1),
                positive,
                negative,
            )

        // Get daily net changes within the range
        val dailyNets =
            ledgerSplitRepository.dailyNetByAccount(
                accountId,
                dateFrom,
                dateTo,
                positive,
                negative,
            )
        val dailyNetMap = dailyNets.associate { it.txnDate to it.netMinor }

        // Build cumulative timeseries
        val points = mutableListOf<BalanceTimeseriesPointDto>()
        var runningBalance = openingBalance
        var currentDate = dateFrom
        while (!currentDate.isAfter(dateTo)) {
            runningBalance += dailyNetMap.getOrDefault(currentDate, 0L)
            points.add(BalanceTimeseriesPointDto(date = currentDate, balanceMinor = runningBalance))
            currentDate = currentDate.plusDays(1)
        }

        return AccountBalanceTimeseriesDto(
            accountId = requireNotNull(account.id),
            accountName = account.name,
            accountType = account.type.name,
            currency = requireNotNull(account.currency?.code),
            points = points,
        )
    }

    private companion object {
        const val DEBT_PAYMENT_CATEGORY_NAME = "Debt Payment"
        const val MODE_INDIVIDUAL = "INDIVIDUAL"
        const val MODE_HOUSEHOLD = "HOUSEHOLD"
        val DEBIT_NORMAL_TYPES = setOf(AccountType.ASSET, AccountType.EXPENSE)

        fun MonthlyExpenseProjection.toDto() =
            MonthlyExpenseDto(
                expenseAccountId = expenseAccountId,
                expenseAccountName = expenseAccountName,
                categoryTagId = categoryTagId,
                categoryTagName = categoryTagName,
                categoryTagColor = categoryTagColor,
                currency = currency,
                netMinor = netMinor,
            )

        fun LiabilityPaymentProjection.toDebtPaymentDto() =
            MonthlyExpenseDto(
                expenseAccountId = liabilityAccountId,
                expenseAccountName = liabilityAccountName,
                categoryTagId = null,
                categoryTagName = DEBT_PAYMENT_CATEGORY_NAME,
                categoryTagColor = null,
                currency = currency,
                netMinor = netMinor,
            )
    }
}
