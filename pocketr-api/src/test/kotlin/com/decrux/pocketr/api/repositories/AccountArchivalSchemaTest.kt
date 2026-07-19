package com.decrux.pocketr.api.repositories

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.db.ledger.Account
import com.decrux.pocketr.api.entities.db.ledger.AccountStatus
import com.decrux.pocketr.api.entities.db.ledger.AccountType
import com.decrux.pocketr.api.entities.db.ledger.Currency
import com.decrux.pocketr.api.testsupport.UseFlywayPostgresDb
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

@DataJpaTest
@UseFlywayPostgresDb
@DisplayName("Account archival schema")
class AccountArchivalSchemaTest
    @Autowired
    constructor(
        private val accountRepository: AccountRepository,
        private val testEntityManager: TestEntityManager,
    ) {
        @Test
        @DisplayName("rejects duplicate active names")
        fun rejectsDuplicateActiveNames() {
            val fixture = persistFixture()
            accountRepository.saveAndFlush(fixture.account())

            assertThrows(DataIntegrityViolationException::class.java) {
                accountRepository.saveAndFlush(fixture.account())
            }
        }

        @Test
        @DisplayName("allows name reuse after archive")
        fun allowsNameReuseAfterArchive() {
            val fixture = persistFixture()
            val archived = accountRepository.saveAndFlush(fixture.account())
            archived.archive(Instant.parse("2026-07-12T10:00:00Z"))
            accountRepository.saveAndFlush(archived)

            assertDoesNotThrow {
                accountRepository.saveAndFlush(fixture.account())
            }
        }

        @Test
        @DisplayName("rejects inconsistent status and archive timestamp")
        fun rejectsInconsistentLifecycleState() {
            val fixture = persistFixture()

            assertThrows(DataIntegrityViolationException::class.java) {
                accountRepository.saveAndFlush(
                    fixture.account(status = AccountStatus.ARCHIVED, archivedAt = null),
                )
            }
        }

        private fun persistFixture(): Fixture {
            val owner =
                testEntityManager.persistAndFlush(
                    User(password = "encoded", email = "archive-schema@example.com"),
                )
            val currency = testEntityManager.persistAndFlush(Currency(code = "EUR", minorUnit = 2, name = "Euro"))
            return Fixture(owner, currency)
        }

        private data class Fixture(
            val owner: User,
            val currency: Currency,
        ) {
            fun account(
                status: AccountStatus = AccountStatus.ACTIVE,
                archivedAt: Instant? = null,
            ) = Account(
                owner = owner,
                name = "Checking",
                type = AccountType.ASSET,
                currency = currency,
                status = status,
                archivedAt = archivedAt,
            )
        }
    }
