create database if not exists clientdb;
create database if not exists customer;
create database if not exists entitlementdb;
create database if not exists authdb;
create database if not exists coredb;
create database if not exists keycloakdb;

grant all privileges on clientdb.* to 'client'@'%';
grant all privileges on customer.* to 'client'@'%';
grant all privileges on entitlementdb.* to 'client'@'%';
grant all privileges on authdb.* to 'client'@'%';
grant all privileges on coredb.* to 'client'@'%';
grant all privileges on keycloakdb.* to 'client'@'%';
flush privileges;
