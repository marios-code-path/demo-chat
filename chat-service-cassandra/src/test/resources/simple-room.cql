DROP KEYSPACE IF EXISTS chat;

CREATE KEYSPACE chat WITH replication={'class' : 'SimpleStrategy', 'replication_factor':1};
USE chat;

CREATE TABLE chat_room(
  id UUID,
  name varchar,
  timestamp timestamp,
  members set<UUID>,
  PRIMARY KEY(id)
);