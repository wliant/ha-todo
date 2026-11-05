-- A member's colour scheme (requirements/0004 R34).
--
-- requirements/0003 explicitly ruled out "theming or a user-chosen colour
-- scheme beyond honouring the device's light or dark preference", and the UI
-- honoured prefers-color-scheme alone. That is wrong for a shared household
-- deployment: the preference belongs to the person, not to the browser they
-- happen to be holding, so it has to follow them to another device.
--
-- SYSTEM is the default and reproduces exactly the old behaviour, so a member
-- who never opens the profile screen notices nothing.
ALTER TABLE app_user ADD COLUMN theme_preference VARCHAR(8) NOT NULL DEFAULT 'SYSTEM';

ALTER TABLE app_user ADD CONSTRAINT ck_app_user_theme_preference
    CHECK (theme_preference IN ('LIGHT', 'DARK', 'SYSTEM'));
