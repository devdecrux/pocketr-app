package com.decrux.pocketr.api.entities.dtos;

import java.util.List;
import java.util.regex.Pattern;

final class RequestDtoValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern ISO_CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");

    private RequestDtoValidator() {
    }

    static void requireNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }

    static void requireNotBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    static void requireMaxLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be at most " + maxLength + " characters");
        }
    }

    static void requireLengthInRange(String value, int minLength, int maxLength, String fieldName) {
        if (value == null) {
            return;
        }

        int length = value.length();
        if (length < minLength || length > maxLength) {
            throw new IllegalArgumentException(
                fieldName + " length must be between " + minLength + " and " + maxLength + " characters"
            );
        }
    }

    static void requirePositive(long value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than 0");
        }
    }

    static void requireMinSize(List<?> values, int minSize, String fieldName) {
        requireNotNull(values, fieldName);
        if (values.size() < minSize) {
            throw new IllegalArgumentException(fieldName + " must contain at least " + minSize + " items");
        }
    }

    static void requireCurrencyCode(String value, String fieldName) {
        requireNotBlank(value, fieldName);
        if (!ISO_CURRENCY_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " must be a 3-letter uppercase currency code");
        }
    }

    static void requireEmail(String value, String fieldName) {
        requireNotBlank(value, fieldName);
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " must be a valid email address");
        }
    }
}
