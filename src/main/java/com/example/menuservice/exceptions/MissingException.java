package com.example.menuservice.exceptions;

public class MissingException extends RuntimeException {
    public MissingException(String message) {
        super(message);
    }
}
