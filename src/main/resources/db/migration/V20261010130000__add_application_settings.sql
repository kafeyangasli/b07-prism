CREATE TABLE application_settings (
    setting_key VARCHAR(100) NOT NULL,
    setting_value VARCHAR(2000) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_application_settings PRIMARY KEY (setting_key)
);
