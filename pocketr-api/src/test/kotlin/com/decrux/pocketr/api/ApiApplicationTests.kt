package com.decrux.pocketr.api

import com.decrux.pocketr.api.testsupport.UsePostgresDb
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@UsePostgresDb
@Disabled("Temporarily disabled until a PostgreSQL test database is provisioned in CI/local test environment")
class ApiApplicationTests {
    @Test
    fun contextLoads() {
    }
}
