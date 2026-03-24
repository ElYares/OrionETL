package com.elyares.etl.interfaces.rest.handler;

import com.elyares.etl.shared.exception.EtlException;
import com.elyares.etl.shared.exception.ExecutionConflictException;
import com.elyares.etl.shared.exception.ExecutionNotFoundException;
import com.elyares.etl.shared.exception.PipelineNotFoundException;
import com.elyares.etl.shared.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Mapeo centralizado de errores REST.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PipelineNotFoundException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> handlePipelineNotFound(PipelineNotFoundException ex,
                                                                                         HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage(), null, request);
    }

    @ExceptionHandler(ExecutionNotFoundException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> handleExecutionNotFound(ExecutionNotFoundException ex,
                                                                                          HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage(), null, request);
    }

    @ExceptionHandler(ExecutionConflictException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> handleExecutionConflict(ExecutionConflictException ex,
                                                                                          HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage(), null, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex,
                                                                                       HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
            .map(this::formatFieldError)
            .toList();
        return build(HttpStatus.BAD_REQUEST, "ETL_REQUEST_INVALID", "Request validation failed", details, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                                            HttpServletRequest request) {
        List<String> details = ex.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .toList();
        return build(HttpStatus.BAD_REQUEST, "ETL_REQUEST_INVALID", "Request validation failed", details, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                                       HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "ETL_REQUEST_INVALID", "Request body is missing or invalid", null, request);
    }

    @ExceptionHandler(EtlException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> handleEtlException(EtlException ex,
                                                                                     HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getErrorCode(), ex.getMessage(), null, request);
    }

    @ExceptionHandler(Exception.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> handleUnhandled(Exception ex,
                                                                                  HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "ETL_UNEXPECTED_ERROR", "Unexpected server error", null, request);
    }

    private org.springframework.http.ResponseEntity<ErrorResponse> build(HttpStatus status,
                                                                         String errorCode,
                                                                         String message,
                                                                         List<String> details,
                                                                         HttpServletRequest request) {
        return org.springframework.http.ResponseEntity.status(status)
            .body(new ErrorResponse(errorCode, message, details, request.getRequestURI(), java.time.Instant.now()));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
