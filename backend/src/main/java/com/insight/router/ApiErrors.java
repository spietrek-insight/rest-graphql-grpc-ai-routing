package com.insight.router;

import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        IllegalArgumentException.class
    })
    ResponseEntity<ProblemDetail> invalid(Exception exception) {
        return ResponseEntity.badRequest()
                .body(
                        ProblemDetail.forStatusAndDetail(
                                HttpStatus.BAD_REQUEST,
                                "Invalid request. Use a message of 1–2000 characters, up to 20 history turns, and a supported protocol."));
    }
}
