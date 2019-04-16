DROP KEYSPACE IF EXISTS chat;

CREATE KEYSPACE chat WITH replication={'class' : 'SimpleStrategy', 'replication_factor':1};
USE chat;

CREATE TABLE chat_message (
    msg_id TIMEUUID,
    user_id UUID,
    room_id UUID,
    text    varchar,
    msg_time timestamp,
    visible Boolean,
    PRIMARY KEY (msg_id));

CREATE TABLE chat_message_user (
msg_id TIMEUUID,
user_id UUID,
room_id UUID,
text    varchar,
msg_time timestamp,
visible Boolean,
PRIMARY KEY (user_id, msg_id))
WITH CLUSTERING ORDER BY (msg_id DESC);

CREATE TABLE chat_message_room (
msg_id TIMEUUID,
user_id UUID,
room_id UUID,
text    varchar,
msg_time timestamp,
visible Boolean,
PRIMARY KEY (room_id, msg_id))
WITH CLUSTERING ORDER BY (msg_id DESC);


