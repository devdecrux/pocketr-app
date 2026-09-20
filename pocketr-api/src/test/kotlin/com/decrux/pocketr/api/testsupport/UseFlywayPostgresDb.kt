package com.decrux.pocketr.api.testsupport

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.support.TestPropertySourceUtils
import org.testcontainers.containers.PostgreSQLContainer
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
@ContextConfiguration(initializers = [FlywayPostgresTestContainerInitializer::class])
annotation class UseFlywayPostgresDb

class FlywayPostgresTestContainerInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        FlywayTestPostgresContainer.start()
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
            applicationContext,
            "spring.datasource.driver-class-name=org.postgresql.Driver",
            "spring.datasource.url=${FlywayTestPostgresContainer.container.jdbcUrl}",
            "spring.datasource.username=${FlywayTestPostgresContainer.container.username}",
            "spring.datasource.password=${FlywayTestPostgresContainer.container.password}",
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=validate",
        )
    }
}

private object FlywayTestPostgresContainer {
    val container =
        FlywayKotlinPostgreSQLContainer("postgres:17.4-alpine")
            .withDatabaseName("pocketr_flyway_test_db")
            .withUsername("pocketr_user")
            .withPassword("pocketr_password")

    @Volatile
    private var started = false

    @Synchronized
    fun start() {
        if (!started) {
            container.start()
            started = true
        }
    }
}

private class FlywayKotlinPostgreSQLContainer(
    imageName: String,
) : PostgreSQLContainer<FlywayKotlinPostgreSQLContainer>(imageName)
