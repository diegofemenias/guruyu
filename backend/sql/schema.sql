CREATE DATABASE IF NOT EXISTS guruyu CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE guruyu;

CREATE TABLE IF NOT EXISTS devices (
    uuid CHAR(36) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL DEFAULT '',
    is_enabled TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_enabled (is_enabled)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS location_reports (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    device_uuid CHAR(36) NOT NULL,
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    reported_at DATETIME NOT NULL,
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_device_reported (device_uuid, reported_at DESC),
    CONSTRAINT fk_location_device FOREIGN KEY (device_uuid) REFERENCES devices(uuid) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS admin_users (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Usuario: admin / Contraseña: admin123 (cambiar en producción)
INSERT INTO admin_users (username, password_hash) VALUES
('admin', '$2y$12$bhF6SWS0zYiLjCtkKigGQOAzhvmQ0tA90oSIZSjdaO7m3wy.l3UPa');
