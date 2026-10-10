-- V7__user_avatar_and_password_reset.sql
-- Add missing columns in users table for avatar and password reset

ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_code VARCHAR(10);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_expires_at TIMESTAMP WITH TIME ZONE;
