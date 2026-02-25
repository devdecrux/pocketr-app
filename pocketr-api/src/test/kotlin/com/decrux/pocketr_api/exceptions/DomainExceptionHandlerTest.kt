package com.decrux.pocketr_api.exceptions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

@DisplayName("DomainExceptionHandler")
class DomainExceptionHandlerTest {

    private val handler = DomainExceptionHandler()

    @Test
    @DisplayName("maps domain exception to matching ResponseStatusException")
    fun mapsDomainExceptionToResponseStatusException() {
        val ex = assertThrows(ResponseStatusException::class.java) {
            handler.handleDomainHttpException(DomainForbiddenException("Not an active member of this household"))
        }

        assertEquals(HttpStatus.FORBIDDEN, ex.statusCode)
        assertEquals("Not an active member of this household", ex.reason)
    }
}
