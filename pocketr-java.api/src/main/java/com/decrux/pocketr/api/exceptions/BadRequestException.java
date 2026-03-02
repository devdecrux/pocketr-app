package com.decrux.pocketr.api.exceptions;

public class BadRequestException extends IllegalArgumentException {

    public BadRequestException(String message) {
        super(message);
    }
}
