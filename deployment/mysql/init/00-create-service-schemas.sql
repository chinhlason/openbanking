create database if not exists clientdb;
create database if not exists commondb;

grant all privileges on clientdb.* to 'client'@'%';
grant all privileges on commondb.* to 'client'@'%';
flush privileges;
