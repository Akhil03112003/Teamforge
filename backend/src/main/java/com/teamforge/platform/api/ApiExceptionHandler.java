package com.teamforge.platform.api;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiErrors.ErrorBody> parameters() {
        return ResponseEntity.badRequest().body(ApiErrors.body(
            "INVALID_REQUEST", "Check the request parameters.", Map.of()));
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrors.ErrorBody> application(ApiException error) {
        var response=ResponseEntity.status(error.status());
        if (error.code().equals("REQUEST_BUSY") || error.code().equals("IDEMPOTENCY_IN_PROGRESS")) response.header("Retry-After","1");
        return response.body(ApiErrors.body(error.code(), error.getMessage(), Map.of()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrors.ErrorBody> validation(MethodArgumentNotValidException error) {
        var fields = new LinkedHashMap<String, String>();
        error.getBindingResult().getFieldErrors().forEach(field ->
            fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
        return ResponseEntity.badRequest().body(ApiErrors.body(
            "VALIDATION_ERROR", "Check the highlighted fields.", fields));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrors.ErrorBody> malformed() {
        return ResponseEntity.badRequest().body(ApiErrors.body(
            "INVALID_REQUEST", "The request body is invalid.", Map.of()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrors.ErrorBody> duplicate() {
        return ResponseEntity.status(409).body(ApiErrors.body(
            "IDENTITY_CONFLICT", "An account with this email already exists.", Map.of()));
    }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiErrors.ErrorBody> staleProfile() {
        return ResponseEntity.status(409).body(ApiErrors.body(
            "PROFILE_CHANGED", "Your profile changed. Reload it and try again.", Map.of()));
    }
}
