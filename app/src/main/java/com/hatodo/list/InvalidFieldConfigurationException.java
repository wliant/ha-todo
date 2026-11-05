package com.hatodo.list;

/**
 * A configuration that cannot mean anything - carrying the reason, because the
 * member is about to be told it and "invalid" is not an answer.
 */
public class InvalidFieldConfigurationException extends RuntimeException {

    public InvalidFieldConfigurationException(String message) {
        super(message);
    }
}
