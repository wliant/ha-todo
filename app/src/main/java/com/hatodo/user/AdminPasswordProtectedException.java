package com.hatodo.user;

/**
 * The admin's password is reconciled from ADMIN_PASSWORD on every startup
 * (adr/0006), so one set through the API would be silently reverted by the next
 * deploy. Refusing is honest; accepting and discarding is not.
 *
 * <p>Lives here rather than in either web package because it is an invariant of
 * the user row: it holds whoever asks, through whichever surface.
 */
public class AdminPasswordProtectedException extends RuntimeException {

    public AdminPasswordProtectedException() {
        super("The admin password comes from the deployment configuration");
    }
}
