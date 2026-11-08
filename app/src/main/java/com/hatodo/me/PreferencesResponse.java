package com.hatodo.me;

import com.hatodo.user.AppUser;
import com.hatodo.user.ThemePreference;

public record PreferencesResponse(ThemePreference theme, int sidebarListCount) {

    public static PreferencesResponse of(AppUser user) {
        return new PreferencesResponse(user.getThemePreference(), user.getSidebarListCount());
    }
}
