package com.example.menuservice.exceptions;

public class ExistsException extends RuntimeException {
    public ExistsException(String message) {
        super(message);
    }
}