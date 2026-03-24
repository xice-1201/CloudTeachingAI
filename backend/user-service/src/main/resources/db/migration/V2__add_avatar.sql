-- Add avatar column to user table
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS avatar VARCHAR(500);
