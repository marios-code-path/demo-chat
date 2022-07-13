DROP KEYSPACE IF EXISTS chat_long;
CREATE KEYSPACE IF NOT EXISTS chat_long WITH replication={'class' : 'SimpleStrategy', 'replication_factor':'1'};

CREATE TYPE chat_long.event_key_meta(
    kind varchar
);

CREATE TABLE chat_long.keys(
id BIGINT,
kind varchar ,
PRIMARY KEY(id) );


CREATE TABLE chat_long.topic_membership
(
    id       BIGINT,
    member   BIGINT,
    memberOf BIGINT,
    PRIMARY KEY (id)
);

CREATE TABLE chat_long.topic_membership_by_member
(
    id       BIGINT,
    member   BIGINT,
    memberOf BIGINT,
    PRIMARY KEY (member)
);

CREATE TABLE chat_long.topic_membership_by_member_of
(
    id       BIGINT,
    member   BIGINT,
    memberOf BIGINT,
    PRIMARY KEY (memberOf)
);


CREATE TABLE chat_long.chat_message_id (
    msg_id TIMESTAMP,
    user_id BIGINT,
    topic_id BIGINT,
    text    varchar,
    msg_time TIMESTAMP,
    visible Boolean,
    PRIMARY KEY (msg_id, msg_time))
WITH CLUSTERING ORDER BY (msg_time DESC);

CREATE TABLE chat_long.chat_message_user (
    msg_id TIMESTAMP,
    user_id BIGINT,
    topic_id BIGINT,
    text    varchar,
    msg_time timestamp,
    visible Boolean,
    PRIMARY KEY (user_id, msg_time, msg_id))
WITH CLUSTERING ORDER BY (msg_time DESC, msg_id DESC);

CREATE TABLE chat_long.chat_message_topic (
    msg_id TIMESTAMP,
    user_id BIGINT,
    topic_id BIGINT,
    text    varchar,
    msg_time timestamp,
    visible Boolean,
    PRIMARY KEY (topic_id, msg_time, msg_id))
WITH CLUSTERING ORDER BY (msg_time DESC, msg_id DESC);


CREATE TABLE chat_long.chat_room(
  room_id BIGINT,
  name varchar,
  active Boolean,
  PRIMARY KEY(room_id)
);

CREATE TABLE chat_long.chat_room_name(
room_id BIGINT,
name varchar,
active Boolean,
PRIMARY KEY(name, room_id))
WITH CLUSTERING ORDER BY (room_id DESC);


CREATE TABLE chat_long.chat_user
(
    user_id   BIGINT,
    handle    varchar,
    name      varchar,
    image_uri varchar,
    timestamp timestamp,
    PRIMARY KEY (user_id)
);

CREATE TABLE chat_long.chat_user_handle
(
    user_id   BIGINT,
    handle    varchar,
    name      varchar,
    image_uri varchar,
    timestamp timestamp,
    PRIMARY KEY (handle)
);