package com.hatodo.admin;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID id) {
        super("No such user: " + id);
    }
}
