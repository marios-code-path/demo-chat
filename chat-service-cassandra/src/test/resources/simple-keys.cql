DROP KEYSPACE IF EXISTS chat;

CREATE KEYSPACE chat WITH replication={'class' : 'SimpleStrategy', 'replication_factor':1};
USE chat;

CREATE TYPE event_key_type(
    id UUID
);

CREATE TABLE keys(
id UUID,
kind varchar,
PRIMARY KEY(id) )