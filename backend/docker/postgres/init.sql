-- Init databases for WISLA FM MVP (single PostgreSQL instance, two logical DBs per architecture)
CREATE USER wisla WITH PASSWORD 'wisla';
CREATE DATABASE wisla_fm OWNER wisla;
CREATE DATABASE wisla_fm_adapter;
CREATE DATABASE wisla_fm_adapter_2;

GRANT ALL PRIVILEGES ON DATABASE wisla_fm TO wisla;
GRANT ALL PRIVILEGES ON DATABASE wisla_fm_adapter TO postgres;
GRANT ALL PRIVILEGES ON DATABASE wisla_fm_adapter_2 TO postgres;
