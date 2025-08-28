-- Database initialization script for TN-ITS prototype
-- Connect to the FREEPDB1 pluggable database and grant permissions

ALTER SESSION SET CONTAINER=FREEPDB1;

-- Grant DBA privileges to the tnits user for development convenience
GRANT DBA TO tnits;
