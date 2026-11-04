package com.hatodo.me;

import com.hatodo.user.ThemePreference;
import jakarta.validation.constraints.NotNull;

/**
 * An unknown theme is rejected by Jackson before any service runs, because the
 * component is the enum rather than a string.
 */
public record PreferencesRequest(@NotNull ThemePreference theme) {
}
