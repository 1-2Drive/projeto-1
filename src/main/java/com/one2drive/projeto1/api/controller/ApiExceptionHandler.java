package com.one2drive.projeto1.api.controller;

import com.one2drive.projeto1.api.model.ApiCommandResponse;
import com.one2drive.projeto1.inference.ExpertSystemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiCommandResponse> notFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ApiCommandResponse(false, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiCommandResponse> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
            .body(new ApiCommandResponse(false, ex.getMessage()));
    }

    /** The requested rule engine could not start, e.g. SWI-Prolog is not installed. */
    @ExceptionHandler(ExpertSystemException.class)
    public ResponseEntity<ApiCommandResponse> engineUnavailable(ExpertSystemException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ApiCommandResponse(false, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiCommandResponse> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage() == null ? "Invalid request" : error.getDefaultMessage())
            .orElse("Invalid request");
        return ResponseEntity.badRequest().body(new ApiCommandResponse(false, message));
    }
}
