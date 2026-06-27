package com.scheduly.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends AppException {
    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
