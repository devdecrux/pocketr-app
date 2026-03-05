package com.decrux.pocketr.api.testsupport

import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS

@Target(CLASS)
@Retention(RUNTIME)
@MustBeDocumented
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.test.database.replace=NONE",
        "app.security.csrf-cookie-path=/",
    ],
)
@ContextConfiguration(initializers = [PostgresTestContainerInitializer::class])
annotation class UsePostgresDb
