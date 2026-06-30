--liquibase formatted sql
--changeset wisla:001-extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
