DROP KEYSPACE IF EXISTS chat;

CREATE KEYSPACE chat WITH replication={'class' : 'SimpleStrategy', 'replication_factor':1};
USE chat;

CREATE TABLE chat_user(
user_id UUID,
handle varchar,
name varchar,
timestamp timestamp,
PRIMARY KEY(user_id));

CREATE TABLE chat_user_handle(
user_id UUID,
handle varchar,
name varchar,
timestamp timestamp,
PRIMARY KEY(handle));