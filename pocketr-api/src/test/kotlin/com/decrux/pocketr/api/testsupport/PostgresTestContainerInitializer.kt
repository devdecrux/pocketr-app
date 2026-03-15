package com.decrux.pocketr.api.testsupport

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.support.TestPropertySourceUtils
import org.testcontainers.containers.PostgreSQLContainer

class PostgresTestContainerInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        TestPostgresContainer.start()
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
            applicationContext,
            "spring.datasource.driver-class-name=org.postgresql.Driver",
            "spring.datasource.url=${TestPostgresContainer.container.jdbcUrl}",
            "spring.datasource.username=${TestPostgresContainer.container.username}",
            "spring.datasource.password=${TestPostgresContainer.container.password}",
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
        )
    }
}

private object TestPostgresContainer {
    val container =
        KotlinPostgreSQLContainer("postgres:17.4-alpine")
            .withDatabaseName("pocketr_test_db")
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

private class KotlinPostgreSQLContainer(
    imageName: String,
) : PostgreSQLContainer<KotlinPostgreSQLContainer>(imageName)
