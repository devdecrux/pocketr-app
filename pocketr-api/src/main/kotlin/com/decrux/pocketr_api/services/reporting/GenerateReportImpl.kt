package com.decrux.pocketr_api.services.reporting

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.ledger.AccountType
import com.decrux.pocketr_api.entities.db.ledger.SplitSide
import com.decrux.pocketr_api.entities.dtos.AccountBalanceSummaryDto
import com.decrux.pocketr_api.entities.dtos.AccountBalanceTimeseriesDto
import com.decrux.pocketr_api.entities.dtos.BalanceTimeseriesPointDto
import com.decrux.pocketr_api.entities.dtos.MonthlyExpenseDto
import com.decrux.pocketr_api.exceptions.DomainBadRequestException
import com.decrux.pocketr_api.exceptions.DomainForbiddenException
import com.decrux.pocketr_api.exceptions.DomainNotFoundException
import com.decrux.pocketr_api.repositories.AccountRepository
import com.decrux.pocketr_api.repositories.LedgerSplitRepository
import com.decrux.pocketr_api.services.household.ManageHousehold
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
    ): List<MonthlyExpenseDto> {
        val monthStart = period.atDay(1)
        val monthEnd = period.plusMonths(1).atDay(1)

        val rows = when (mode.uppercase()) {
            MODE_INDIVIDUAL -> {
                val userId = requireNotNull(user.userId) { "User ID must not be null" }
                ledgerSplitRepository.monthlyExpensesByUser(userId, monthStart, monthEnd, SplitSide.DEBIT, SplitSide.CREDIT)
            }

            MODE_HOUSEHOLD -> {
                val hId = householdId
                    ?: throw DomainBadRequestException("householdId is required for household mode")
                val userId = requireNotNull(user.userId) { "User ID must not be null" }
                if (!manageHousehold.isActiveMember(hId, userId)) {
                    throw DomainForbiddenException("Not an active member of this household")
                }
                ledgerSplitRepository.monthlyExpensesByHousehold(hId, monthStart, monthEnd, SplitSide.DEBIT, SplitSide.CREDIT)
            }

            else -> throw DomainBadRequestException("Invalid mode: $mode. Must be INDIVIDUAL or HOUSEHOLD")
        }

        return rows.map { row ->
            MonthlyExpenseDto(
                expenseAccountId = row[0] as UUID,
                expenseAccountName = row[1] as String,
                categoryTagId = row[2] as UUID?,
                categoryTagName = row[3] as String?,
                currency = row[4] as String,
                netMinor = row[5] as Long,
            )
        }
    }

    @Transactional(readOnly = true)
    override fun getAllAccountBalances(user: User, asOf: LocalDate): List<AccountBalanceSummaryDto> {
        val userId = requireNotNull(user.userId) { "User ID must not be null" }
        val accounts = accountRepository.findByOwnerUserId(userId)

        return accounts.map { account ->
            val accountId = requireNotNull(account.id)
            val isDebitNormal = account.type in DEBIT_NORMAL_TYPES
            val balanceMinor = if (isDebitNormal) {
                ledgerSplitRepository.computeBalance(accountId, asOf, SplitSide.DEBIT, SplitSide.CREDIT)
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
            throw DomainBadRequestException("dateFrom must be before or equal to dateTo")
        }

        val userId = requireNotNull(user.userId) { "User ID must not be null" }

        val account = accountRepository.findById(accountId)
            .orElseThrow { DomainNotFoundException("Account not found") }

        if (account.owner?.userId != userId) {
            throw DomainForbiddenException("Not the owner of this account")
        }

        val isDebitNormal = account.type in DEBIT_NORMAL_TYPES
        val positive = if (isDebitNormal) SplitSide.DEBIT else SplitSide.CREDIT
        val negative = if (isDebitNormal) SplitSide.CREDIT else SplitSide.DEBIT

        // Get opening balance (everything before dateFrom)
        val openingBalance = ledgerSplitRepository.computeBalance(
            accountId, dateFrom.minusDays(1), positive, negative,
        )

        // Get daily net changes within the range
        val dailyNets = ledgerSplitRepository.dailyNetByAccount(
            accountId, dateFrom, dateTo, positive, negative,
        )
        val dailyNetMap = dailyNets.associate { row ->
            (row[0] as LocalDate) to (row[1] as Long)
        }

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
        const val MODE_INDIVIDUAL = "INDIVIDUAL"
        const val MODE_HOUSEHOLD = "HOUSEHOLD"
        val DEBIT_NORMAL_TYPES = setOf(AccountType.ASSET, AccountType.EXPENSE)
    }
}
