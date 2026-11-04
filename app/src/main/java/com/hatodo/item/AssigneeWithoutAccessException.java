package com.hatodo.item;

/**
 * An item cannot be assigned to somebody who cannot see the list (R24).
 *
 * <p>Checked in the service, not left to the picker: the dropdown offering only
 * the right people is a convenience, never the control.
 */
public class AssigneeWithoutAccessException extends RuntimeException {

    public AssigneeWithoutAccessException() {
        super("That person cannot see this list");
    }
}
