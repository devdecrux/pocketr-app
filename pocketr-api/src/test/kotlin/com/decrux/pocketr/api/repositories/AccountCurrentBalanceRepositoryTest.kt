package com.decrux.pocketr.api.repositories

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.db.ledger.Account
import com.decrux.pocketr.api.entities.db.ledger.AccountType
import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.testsupport.UsePostgresDb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import java.util.*

@DataJpaTest
@UsePostgresDb
@DisplayName("AccountCurrentBalanceRepository")
class AccountCurrentBalanceRepositoryTest
    @Autowired
    constructor(
        private val accountCurrentBalanceRepository: AccountCurrentBalanceRepository,
        private val testEntityManager: TestEntityManager,
    ) {
        @Test
        @DisplayName("addDelta inserts a new row for missing account")
        fun addDeltaInserts() {
            val accountId = persistAccount()

            accountCurrentBalanceRepository.addDelta(accountId, 1500L)
            testEntityManager.flush()
            testEntityManager.clear()

            val current = accountCurrentBalanceRepository.findById(accountId).orElseThrow()
            assertEquals(1500L, current.rawBalanceMinor)
            assertNotNull(current.updatedAt)
        }

        @Test
        @DisplayName("addDelta increments existing row")
        fun addDeltaIncrements() {
            val accountId = persistAccount()

            accountCurrentBalanceRepository.addDelta(accountId, 2000L)
            accountCurrentBalanceRepository.addDelta(accountId, -350L)
            testEntityManager.flush()
            testEntityManager.clear()

            val current = accountCurrentBalanceRepository.findById(accountId).orElseThrow()
            assertEquals(1650L, current.rawBalanceMinor)
        }

        @Test
        @DisplayName("findAllByAccountIdIn returns balances only for existing rows")
        fun findAllByAccountIdInReturnsExistingRows() {
            val firstId = persistAccount()
            val secondId = persistAccount()
            val missingId = UUID.randomUUID()

            accountCurrentBalanceRepository.addDelta(firstId, 100L)
            accountCurrentBalanceRepository.addDelta(secondId, -300L)
            testEntityManager.flush()
            testEntityManager.clear()

            val rows = accountCurrentBalanceRepository.findAllByAccountIdIn(listOf(firstId, secondId, missingId))
            val byId = rows.associateBy { it.accountId }

            assertEquals(2, rows.size)
            assertEquals(100L, byId.getValue(firstId).rawBalanceMinor)
            assertEquals(-300L, byId.getValue(secondId).rawBalanceMinor)
            assertTrue(missingId !in byId.keys)
        }

        @Test
        @DisplayName("countAccountsBalanceMismatch reports projection drift")
        fun countAccountsBalanceMismatchReportsDrift() {
            assertEquals(0L, accountCurrentBalanceRepository.countAccountsBalanceMismatch())

            val accountId = persistAccount()
            accountCurrentBalanceRepository.addDelta(accountId, 50L)
            testEntityManager.flush()
            testEntityManager.clear()

            assertEquals(1L, accountCurrentBalanceRepository.countAccountsBalanceMismatch())
        }

        @Test
        @DisplayName("findAccountsBalanceMismatch returns mismatched account ids")
        fun findAccountsBalanceMismatchReturnsIds() {
            val accountId = persistAccount()
            accountCurrentBalanceRepository.addDelta(accountId, 50L)
            testEntityManager.flush()
            testEntityManager.clear()

            val mismatchIds = accountCurrentBalanceRepository.findAccountsBalanceMismatch().toSet()
            assertEquals(setOf(accountId), mismatchIds)
        }

        private fun persistAccount(): UUID {
            val currency =
                testEntityManager.entityManager.find(Currency::class.java, "EUR")
                    ?: Currency(
                        code = "EUR",
                        minorUnit = 2,
                        name = "Euro",
                    ).also { testEntityManager.persist(it) }

            val user =
                User(
                    password = "encoded-password",
                    email = "balance-${UUID.randomUUID()}@test.com",
                )
            testEntityManager.persist(user)

            val account =
                Account(
                    owner = user,
                    name = "Account-${UUID.randomUUID()}",
                    type = AccountType.ASSET,
                    currency = currency,
                )
            testEntityManager.persistAndFlush(account)
            return requireNotNull(account.id)
        }
    }
