-- app_name: the display name shown on the login screen, sidebar and browser tab, editable by a
-- SETTINGS_MANAGE user from Settings so each client can use their own name (e.g. "Betty")
-- without a redeploy. Defaults to the name the app has always shown.

ALTER TABLE system_settings ADD COLUMN app_name VARCHAR(60) NOT NULL DEFAULT 'ERP Suite';
