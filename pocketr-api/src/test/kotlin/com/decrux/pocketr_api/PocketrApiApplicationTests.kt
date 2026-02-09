package com.decrux.pocketr_api

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Disabled("Temporarily disabled until a PostgreSQL test database is provisioned in CI/local test environment")
class PocketrApiApplicationTests {

	@Test
	fun contextLoads() {
	}

}
