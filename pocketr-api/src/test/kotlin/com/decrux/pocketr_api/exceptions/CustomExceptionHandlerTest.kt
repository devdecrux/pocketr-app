package com.decrux.pocketr_api.exceptions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

@DisplayName("CustomExceptionHandler")
class CustomExceptionHandlerTest {

    private val handler = CustomExceptionHandler()

    @Test
    @DisplayName("maps bad request exception to 400")
    fun mapsBadRequestExceptionToResponseStatusException() {
        val ex = assertThrows(ResponseStatusException::class.java) {
            handler.handleIllegalArgumentException(BadRequestException("Invalid payload"))
        }

        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("Invalid payload", ex.reason)
    }

    @Test
    @DisplayName("maps forbidden exception to 403")
    fun mapsForbiddenExceptionToResponseStatusException() {
        val ex = assertThrows(ResponseStatusException::class.java) {
            handler.handleAccessDeniedException(ForbiddenException("Not an active member of this household"))
        }

        assertEquals(HttpStatus.FORBIDDEN, ex.statusCode)
        assertEquals("Not an active member of this household", ex.reason)
    }

    @Test
    @DisplayName("maps not found exception to 404")
    fun mapsNotFoundExceptionToResponseStatusException() {
        val ex = assertThrows(ResponseStatusException::class.java) {
            handler.handleNoSuchElementException(NotFoundException("Account not found"))
        }

        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
        assertEquals("Account not found", ex.reason)
    }

    @Test
    @DisplayName("maps authentication exception to 401")
    fun mapsAuthenticationExceptionToResponseStatusException() {
        val ex = assertThrows(ResponseStatusException::class.java) {
            handler.handleAuthenticationException(UserNotFoundException("User with email test@test.com not found"))
        }

        assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
        assertEquals("User with email test@test.com not found", ex.reason)
    }
}
