package com.swiftfleet.service;

import org.springframework.http.HttpStatus;

public class FleetException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public FleetException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}

