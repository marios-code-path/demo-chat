DROP KEYSPACE IF EXISTS chat;

CREATE KEYSPACE chat WITH replication={'class' : 'SimpleStrategy', 'replication_factor':1};
USE chat;

CREATE TABLE chat_message (
    user_id UUID,
    room_id UUID,
    text    varchar,
    msg_time timestamp,
    visible Boolean,
    PRIMARY KEY ((room_id, user_id), msg_time))
  WITH CLUSTERING ORDER BY (msg_time DESC)

