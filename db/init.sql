create database db;
create user simpleauth with encrypted password 'simpleauth';
grant all privileges on database db to simpleauth;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO simpleauth;
ALTER ROLE simpleauth SUPERUSER;