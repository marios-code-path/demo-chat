DROP KEYSPACE IF EXISTS chat;

CREATE KEYSPACE chat WITH replication={'class' : 'SimpleStrategy', 'replication_factor':1};
USE chat;

CREATE TABLE chat_user(
id UUID,
handle varchar,
name varchar,
timestamp timestamp,
PRIMARY KEY(id, handle, name));