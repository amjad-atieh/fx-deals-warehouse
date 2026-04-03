package com.bloomberg.fxdeals.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void testHandleValidationExceptions() {
        // Arrange
        MethodArgumentNotValidException ex = Mockito.mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = Mockito.mock(BindingResult.class);
        FieldError fieldError = new FieldError("objectName", "fieldName", "must not be null");
        Mockito.when(ex.getBindingResult()).thenReturn(bindingResult);
        Mockito.when(bindingResult.getAllErrors()).thenReturn(Collections.singletonList(fieldError));

        // Act
        ResponseEntity<ErrorResponse> responseEntity = exceptionHandler.handleValidationExceptions(ex);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        ErrorResponse response = responseEntity.getBody();
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.status());
        assertEquals("Validation failed", response.message());
        assertEquals("Bad Request", response.error());
        assertNotNull(response.timestamp());
        assertEquals(1, response.details().size());
        assertEquals("must not be null", response.details().get("fieldName"));
    }

    @Test
    void testHandleConstraintViolationException() {
        // Arrange
        ConstraintViolation<?> violation = Mockito.mock(ConstraintViolation.class);
        Path path = Mockito.mock(Path.class);
        Mockito.when(path.toString()).thenReturn("propertyPath");
        Mockito.when(violation.getPropertyPath()).thenReturn(path);
        Mockito.when(violation.getMessage()).thenReturn("must be positive");

        Set<ConstraintViolation<?>> violations = new HashSet<>();
        violations.add(violation);
        ConstraintViolationException ex = new ConstraintViolationException("Constraint violation", violations);

        // Act
        ResponseEntity<ErrorResponse> responseEntity = exceptionHandler.handleConstraintViolationException(ex);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        ErrorResponse response = responseEntity.getBody();
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.status());
        assertEquals("Constraint violation", response.message());
        assertEquals("Bad Request", response.error());
        assertNotNull(response.timestamp());
        assertEquals(1, response.details().size());
        assertEquals("must be positive", response.details().get("propertyPath"));
    }

    @Test
    void testHandleDealNotFound() {
        // Arrange
        DealNotFoundException ex = new DealNotFoundException("Deal with id 1 not found");

        // Act
        ResponseEntity<ErrorResponse> responseEntity = exceptionHandler.handleDealNotFound(ex);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, responseEntity.getStatusCode());
        ErrorResponse response = responseEntity.getBody();
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND.value(), response.status());
        assertEquals("Deal with id 1 not found", response.message());
        assertEquals("Not Found", response.error());
        assertNotNull(response.timestamp());
        assertNull(response.details());
    }

    @Test
    void testHandleGenericException() {
        // Arrange
        Exception ex = new Exception("Unexpected failure");

        // Act
        ResponseEntity<ErrorResponse> responseEntity = exceptionHandler.handleGenericException(ex);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
        ErrorResponse response = responseEntity.getBody();
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.status());
        assertEquals("An unexpected error occurred", response.message());
        assertEquals("Internal Server Error", response.error());
        assertNotNull(response.timestamp());
        assertNull(response.details());
    }
}
