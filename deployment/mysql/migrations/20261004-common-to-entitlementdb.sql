-- One-time local migration for the Common service datasource consolidation.
-- Run only when the legacy Common tables are still stored in commondb.
-- The application datasource must point to entitlementdb after this migration.

CREATE DATABASE IF NOT EXISTS entitlementdb;

CREATE TABLE IF NOT EXISTS entitlementdb.config_client LIKE commondb.config_client;
CREATE TABLE IF NOT EXISTS entitlementdb.config_entry LIKE commondb.config_entry;
CREATE TABLE IF NOT EXISTS entitlementdb.config_publish_event LIKE commondb.config_publish_event;
CREATE TABLE IF NOT EXISTS entitlementdb.config_audit_log LIKE commondb.config_audit_log;

INSERT IGNORE INTO entitlementdb.config_client
SELECT * FROM commondb.config_client;

INSERT IGNORE INTO entitlementdb.config_entry
SELECT * FROM commondb.config_entry;

INSERT IGNORE INTO entitlementdb.config_publish_event
SELECT * FROM commondb.config_publish_event;

INSERT IGNORE INTO entitlementdb.config_audit_log
SELECT * FROM commondb.config_audit_log;
