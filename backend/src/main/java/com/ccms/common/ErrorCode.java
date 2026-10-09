package com.ccms.common;

import org.springframework.http.HttpStatus;

/** Error codes are stable strings; the frontend translates them (bn/en). */
public enum ErrorCode {
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCOUNT_BLOCKED(HttpStatus.UNAUTHORIZED),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    DUPLICATE(HttpStatus.CONFLICT),
    CONTRACTOR_REQUIRED(HttpStatus.BAD_REQUEST),
    RATE_NOT_FOUND(HttpStatus.UNPROCESSABLE_ENTITY),
    DAY_FRACTION_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_LOCATION(HttpStatus.UNPROCESSABLE_ENTITY),
    WRONG_PASSWORD(HttpStatus.BAD_REQUEST),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR);

    public final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }
}
