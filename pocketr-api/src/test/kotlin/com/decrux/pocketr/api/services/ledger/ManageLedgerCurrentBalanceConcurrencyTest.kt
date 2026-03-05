package com.decrux.pocketr.api.services.ledger

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.db.ledger.Account
import com.decrux.pocketr.api.entities.db.ledger.AccountType
import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.entities.db.ledger.SplitSide
import com.decrux.pocketr.api.entities.dtos.CreateSplitDto
import com.decrux.pocketr.api.entities.dtos.CreateTransactionDto
import com.decrux.pocketr.api.repositories.AccountCurrentBalanceRepository
import com.decrux.pocketr.api.repositories.AccountRepository
import com.decrux.pocketr.api.repositories.CurrencyRepository
import com.decrux.pocketr.api.repositories.LedgerSplitRepository
import com.decrux.pocketr.api.repositories.LedgerTxnRepository
import com.decrux.pocketr.api.repositories.UserRepository
import com.decrux.pocketr.api.testsupport.UsePostgresDb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@UsePostgresDb
@TestPropertySource(properties = ["ledger.current-balance.fast-path-enabled=true"])
@DisplayName("ManageLedger current balance concurrency")
class ManageLedgerCurrentBalanceConcurrencyTest
    @Autowired
    constructor(
    private val manageLedger: ManageLedger,
    private val userRepository: UserRepository,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val ledgerTxnRepository: LedgerTxnRepository,
    private val ledgerSplitRepository: LedgerSplitRepository,
    private val accountCurrentBalanceRepository: AccountCurrentBalanceRepository,
    ) {
    @Test
    @DisplayName("parallel postings to overlapping accounts keep projection equal to ledger")
    fun parallelPostingsKeepProjectionInSync() {
        accountCurrentBalanceRepository.deleteAll()
        ledgerTxnRepository.deleteAll()
        accountRepository.deleteAll()
        userRepository.deleteAll()

        val eur =
            currencyRepository.findById("EUR").orElseGet {
                currencyRepository.save(
                    Currency(code = "EUR", minorUnit = 2, name = "Euro"),
                )
            }

        val user =
            userRepository.save(
                User(
                    password = "encoded-password",
                    email = "concurrency-${UUID.randomUUID()}@test.com",
                ),
            )

        val accountA = persistAssetAccount(user, eur, "Concurrency A")
        val accountB = persistAssetAccount(user, eur, "Concurrency B")
        val accountC = persistAssetAccount(user, eur, "Concurrency C")
        val accountIds = listOf(requireNotNull(accountA.id), requireNotNull(accountB.id), requireNotNull(accountC.id))

        val tasks = 90
        val threadPool = Executors.newFixedThreadPool(12)
        val start = CountDownLatch(1)
        val done = CountDownLatch(tasks)
        val failures = AtomicInteger(0)
        val today = LocalDate.now()

        repeat(tasks) { index ->
            threadPool.submit {
                try {
                    start.await()
                    when (index % 3) {
                        0 -> {
                            createTransfer(
                                user = user,
                                txnDate = today,
                                fromAccountId = accountIds[0],
                                toAccountId = accountIds[1],
                                amountMinor = 100,
                                description = "A->B#$index",
                            )
                        }

                        1 -> {
                            createTransfer(
                                user = user,
                                txnDate = today,
                                fromAccountId = accountIds[1],
                                toAccountId = accountIds[2],
                                amountMinor = 100,
                                description = "B->C#$index",
                            )
                        }

                        else -> {
                            createTransfer(
                                user = user,
                                txnDate = today,
                                fromAccountId = accountIds[2],
                                toAccountId = accountIds[0],
                                amountMinor = 100,
                                description = "C->A#$index",
                            )
                        }
                    }
                } catch (_: Exception) {
                    failures.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
        }

        start.countDown()
        val completed = done.await(60, TimeUnit.SECONDS)
        threadPool.shutdown()
        threadPool.awaitTermination(30, TimeUnit.SECONDS)

        assertTrue(completed, "Concurrent postings did not finish in time")
        assertEquals(0, failures.get(), "No task should fail")

        val projectionById =
            accountCurrentBalanceRepository
                .findAllByAccountIdIn(accountIds)
                .associate { requireNotNull(it.accountId) to it.rawBalanceMinor }

        val aggregateById =
            ledgerSplitRepository
                .computeRawBalancesByAccountIds(
                    accountIds,
                    today,
                    SplitSide.DEBIT,
                    SplitSide.CREDIT,
                ).associate { it.accountId to it.rawBalance }

        accountIds.forEach { id ->
            assertEquals(
                aggregateById[id] ?: 0L,
                projectionById[id] ?: 0L,
                "Projection must match ledger aggregate for account $id",
            )
        }
    }

    @Test
    @DisplayName("parallel postings to the same account keep projection equal to ledger")
    fun parallelPostingsToSameAccountKeepProjectionInSync() {
        accountCurrentBalanceRepository.deleteAll()
        ledgerTxnRepository.deleteAll()
        accountRepository.deleteAll()
        userRepository.deleteAll()

        val eur =
            currencyRepository.findById("EUR").orElseGet {
                currencyRepository.save(
                    Currency(code = "EUR", minorUnit = 2, name = "Euro"),
                )
            }

        val user =
            userRepository.save(
                User(
                    password = "encoded-password",
                    email = "concurrency-same-${UUID.randomUUID()}@test.com",
                ),
            )

        val hotspot = persistAssetAccount(user, eur, "Concurrency Hotspot")
        val counterparty = persistAssetAccount(user, eur, "Concurrency Counterparty")
        val hotspotId = requireNotNull(hotspot.id)
        val counterpartyId = requireNotNull(counterparty.id)
        val accountIds = listOf(hotspotId, counterpartyId)

        val tasks = 120
        val amountMinor = 100L
        val threadPool = Executors.newFixedThreadPool(12)
        val start = CountDownLatch(1)
        val done = CountDownLatch(tasks)
        val failures = AtomicInteger(0)
        val today = LocalDate.now()

        repeat(tasks) { index ->
            threadPool.submit {
                try {
                    start.await()
                    createTransfer(
                        user = user,
                        txnDate = today,
                        fromAccountId = hotspotId,
                        toAccountId = counterpartyId,
                        amountMinor = amountMinor,
                        description = "Hotspot->Counterparty#$index",
                    )
                } catch (_: Exception) {
                    failures.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
        }

        start.countDown()
        val completed = done.await(60, TimeUnit.SECONDS)
        threadPool.shutdown()
        threadPool.awaitTermination(30, TimeUnit.SECONDS)

        assertTrue(completed, "Concurrent same-account postings did not finish in time")
        assertEquals(0, failures.get(), "No same-account task should fail")

        val projectionById =
            accountCurrentBalanceRepository
                .findAllByAccountIdIn(accountIds)
                .associate { requireNotNull(it.accountId) to it.rawBalanceMinor }

        val aggregateById =
            ledgerSplitRepository
                .computeRawBalancesByAccountIds(
                    accountIds,
                    today,
                    SplitSide.DEBIT,
                    SplitSide.CREDIT,
                ).associate { it.accountId to it.rawBalance }

        accountIds.forEach { id ->
            assertEquals(
                aggregateById[id] ?: 0L,
                projectionById[id] ?: 0L,
                "Projection must match ledger aggregate for account $id",
            )
        }

        assertEquals(-tasks * amountMinor, projectionById.getValue(hotspotId))
        assertEquals(tasks * amountMinor, projectionById.getValue(counterpartyId))
    }

    private fun createTransfer(
        user: User,
        txnDate: LocalDate,
        fromAccountId: UUID,
        toAccountId: UUID,
        amountMinor: Long,
        description: String,
    ) {
        manageLedger.createTransaction(
            dto =
                CreateTransactionDto(
                    txnDate = txnDate,
                    currency = "EUR",
                    description = description,
                    splits =
                        listOf(
                            CreateSplitDto(accountId = fromAccountId, side = "CREDIT", amountMinor = amountMinor),
                            CreateSplitDto(accountId = toAccountId, side = "DEBIT", amountMinor = amountMinor),
                        ),
                ),
            creator = user,
        )
    }

    private fun persistAssetAccount(
        user: User,
        currency: Currency,
        name: String,
    ): Account =
        accountRepository.save(
            Account(
                owner = user,
                name = "$name-${UUID.randomUUID()}",
                type = AccountType.ASSET,
                currency = currency,
            ),
        )
}
