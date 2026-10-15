-- login_json: the wording, layout and colours of the sign-in page, chosen in Settings > Login page.
-- NULL means the built-in sign-in page. Shown before anyone signs in, so it is validated before it is stored.
ALTER TABLE system_settings ADD COLUMN login_json VARCHAR(4000);
-- Room for the extra fonts in the theme JSON as well.
ALTER TABLE system_settings ALTER COLUMN theme_json TYPE VARCHAR(4000);
