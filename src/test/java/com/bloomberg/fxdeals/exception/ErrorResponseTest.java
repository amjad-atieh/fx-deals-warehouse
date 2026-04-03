package com.bloomberg.fxdeals.exception;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorResponseTest {

    @Test
    void testErrorResponse() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> details = Map.of("field", "error");
        ErrorResponse response = new ErrorResponse(now, 400, "Bad Request", "Validation failed", details);

        assertEquals(now, response.timestamp());
        assertEquals(400, response.status());
        assertEquals("Bad Request", response.error());
        assertEquals("Validation failed", response.message());
        assertEquals(details, response.details());
    }
}
