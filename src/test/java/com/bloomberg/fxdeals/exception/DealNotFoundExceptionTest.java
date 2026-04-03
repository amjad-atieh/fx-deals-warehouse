package com.bloomberg.fxdeals.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DealNotFoundExceptionTest {

    @Test
    void testExceptionMessage() {
        String msg = "Custom exception message";
        DealNotFoundException ex = new DealNotFoundException(msg);

        assertEquals(msg, ex.getMessage());
    }
}
