-- theme_json: the look of the app (colours, fonts) chosen in Settings > Appearance, shared by every user.
-- NULL means the built-in look.
ALTER TABLE system_settings ADD COLUMN theme_json VARCHAR(2000);
