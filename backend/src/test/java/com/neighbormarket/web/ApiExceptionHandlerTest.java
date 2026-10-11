package com.neighbormarket.web;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void responseStatusExceptionKeepsBusinessStatusAndReason() {
        var response = handler.responseStatus(new ResponseStatusException(HttpStatus.CONFLICT, "状态已变化"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("CONFLICT", response.getBody().code());
        assertEquals("状态已变化", response.getBody().message());
    }

    @Test
    void databaseConflictDoesNotLeakSqlDetails() {
        var response = handler.dataConflict(new DataIntegrityViolationException("duplicate key secret SQL"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DATA_CONFLICT", response.getBody().code());
        assertFalse(response.getBody().message().contains("SQL"));
        assertFalse(response.getBody().message().contains("secret"));
    }

    @Test
    void unexpectedExceptionReturnsGenericMessage() {
        var response = handler.unexpected(new RuntimeException("internal secret"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
        assertFalse(response.getBody().message().contains("secret"));
    }
}
