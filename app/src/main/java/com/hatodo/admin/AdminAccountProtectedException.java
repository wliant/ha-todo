package com.hatodo.admin;

/**
 * The admin account is undeletable (requirements/0002 R12) so the household
 * cannot lock itself out of administration - including by the admin itself.
 */
public class AdminAccountProtectedException extends RuntimeException {

    public AdminAccountProtectedException() {
        super("The admin account cannot be deleted");
    }
}
