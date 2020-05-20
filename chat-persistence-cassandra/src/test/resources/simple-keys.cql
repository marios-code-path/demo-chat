DROP KEYSPACE IF EXISTS chat;

CREATE KEYSPACE chat WITH replication={'class' : 'SimpleStrategy', 'replication_factor':1};

USE chat;

CREATE TYPE event_key_meta(
    kind varchar
);

CREATE TABLE keys(
id UUID,
kind event_key_meta ,
PRIMARY KEY(id) )