DROP KEYSPACE IF EXISTS chat;

CREATE KEYSPACE chat WITH replication={'class' : 'SimpleStrategy', 'replication_factor':1};
USE chat;

CREATE TABLE chat_message (
    msg_id UUID,
    user_id UUID,
    room_id UUID,
    text    varchar,
    msg_time timestamp,
    visible Boolean,
    PRIMARY KEY ((msg_id), msg_time))
  WITH CLUSTERING ORDER BY (msg_time DESC);

CREATE TABLE chat_message_user (
msg_id UUID,
user_id UUID,
room_id UUID,
text    varchar,
msg_time timestamp,
visible Boolean,
PRIMARY KEY ((user_id), msg_time))
WITH CLUSTERING ORDER BY (msg_time DESC);

CREATE TABLE chat_message_room (
msg_id UUID,
user_id UUID,
room_id UUID,
text    varchar,
msg_time timestamp,
visible Boolean,
PRIMARY KEY ((room_id), msg_time))
WITH CLUSTERING ORDER BY (msg_time DESC);


