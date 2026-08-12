package com.decrux.pocketr.api.repositories

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.db.ledger.Account
import com.decrux.pocketr.api.entities.db.ledger.AccountType
import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.entities.db.ledger.LedgerSplit
import com.decrux.pocketr.api.entities.db.ledger.LedgerTxn
import com.decrux.pocketr.api.entities.db.ledger.SplitSide
import com.decrux.pocketr.api.testsupport.UsePostgresDb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import java.time.LocalDate
import java.util.UUID

@DataJpaTest
@UsePostgresDb
@DisplayName("LedgerSplitRepository")
class LedgerSplitRepositoryTest
    @Autowired
    constructor(
        private val ledgerSplitRepository: LedgerSplitRepository,
        private val testEntityManager: TestEntityManager,
    ) {
        @Test
        @DisplayName("computeLifetimeBalance includes future-dated ledger entries")
        fun computeLifetimeBalanceIncludesFutureEntries() {
            val currency = persistCurrency()
            val user = persistUser()
            val account = persistAccount(user, currency)
            val transaction =
                LedgerTxn(
                    createdBy = user,
                    txnDate = LocalDate.of(2100, 1, 1),
                    description = "Future balance",
                    currency = currency,
                )
            transaction.splits =
                mutableListOf(
                    LedgerSplit(
                        transaction = transaction,
                        account = account,
                        side = SplitSide.DEBIT,
                        amountMinor = 700L,
                    ),
                    LedgerSplit(
                        transaction = transaction,
                        account = account,
                        side = SplitSide.CREDIT,
                        amountMinor = 200L,
                    ),
                )
            testEntityManager.persistAndFlush(transaction)
            testEntityManager.clear()

            val balance =
                ledgerSplitRepository.computeLifetimeBalance(
                    requireNotNull(account.id),
                    SplitSide.DEBIT,
                    SplitSide.CREDIT,
                )

            assertEquals(500L, balance)
        }

        private fun persistCurrency(): Currency =
            testEntityManager.entityManager.find(Currency::class.java, "EUR")
                ?: Currency(
                    code = "EUR",
                    minorUnit = 2,
                    name = "Euro",
                ).also { testEntityManager.persist(it) }

        private fun persistUser(): User =
            User(
                password = "encoded-password",
                email = "lifetime-balance-${UUID.randomUUID()}@test.com",
            ).also { testEntityManager.persist(it) }

        private fun persistAccount(
            user: User,
            currency: Currency,
        ): Account =
            Account(
                owner = user,
                name = "Account-${UUID.randomUUID()}",
                type = AccountType.ASSET,
                currency = currency,
            ).also { testEntityManager.persistAndFlush(it) }
    }
