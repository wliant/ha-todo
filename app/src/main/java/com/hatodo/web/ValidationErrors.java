package com.hatodo.web;

import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * Turns a bean-validation failure into the one-key body the SPA already reads.
 *
 * <p>Without this, a validation failure falls through to Spring's default error
 * body, which the client's {@code errorMessage()} cannot parse - so the UI shows
 * its generic fallback instead of saying what was wrong. Shared so the rule is
 * the same wherever it is applied, while each advice keeps its own wording for
 * its own domain exceptions.
 */
public final class ValidationErrors {

    private ValidationErrors() {
    }

    public static String firstMessage(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(ValidationErrors::describe)
                .orElse("That request was not valid.");
    }

    private static String describe(FieldError error) {
        String message = error.getDefaultMessage();
        if (message == null || message.isBlank()) {
            return "That request was not valid.";
        }
        // Field names are the API's, not prose, so a message that already reads
        // as a sentence is used as-is and one that does not is prefixed.
        return Character.isUpperCase(message.charAt(0))
                ? message
                : error.getField() + " " + message;
    }
}
