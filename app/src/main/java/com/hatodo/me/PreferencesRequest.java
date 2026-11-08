package com.hatodo.me;

import com.hatodo.user.AppUser;
import com.hatodo.user.ThemePreference;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * An unknown theme is rejected by Jackson before any service runs, because the
 * component is the enum rather than a string.
 *
 * @param theme            the member's theme
 * @param sidebarListCount how many lists and collections each sidebar group
 *                         shows (0012 R102). Boxed, and absent means
 *                         "unchanged": Jackson rejects a missing primitive in a
 *                         record, and a client patching only the theme must not
 *                         be made to restate a number it is not editing.
 */
public record PreferencesRequest(
        @NotNull ThemePreference theme,
        @Min(AppUser.MIN_SIDEBAR_LIST_COUNT)
        @Max(AppUser.MAX_SIDEBAR_LIST_COUNT)
        Integer sidebarListCount) {

    /*
     * Named apart from the component it defaults, because a record accessor has
     * to keep its component's type - the same reason the field requests carry
     * pointsOrZero() and isRequired().
     */

    public boolean changesSidebarListCount() {
        return sidebarListCount != null;
    }
}
