DROP TABLE IF EXISTS chat_message_id;
DROP TABLE IF EXISTS chat_message_user;
DROP TABLE IF EXISTS chat_message_topic;

CREATE TABLE chat_message_id (
    msg_id TIMEUUID,
    user_id UUID,
    topic_id UUID,
    text    varchar,
    msg_time timestamp,
    visible Boolean,
    PRIMARY KEY (msg_id, msg_time))
WITH CLUSTERING ORDER BY (msg_time DESC);

CREATE TABLE chat_message_user (
    msg_id TIMEUUID,
    user_id UUID,
    topic_id UUID,
    text    varchar,
    msg_time timestamp,
    visible Boolean,
    PRIMARY KEY (user_id, msg_time, msg_id))
WITH CLUSTERING ORDER BY (msg_time DESC, msg_id DESC);

CREATE TABLE chat_message_topic (
    msg_id TIMEUUID,
    user_id UUID,
    topic_id UUID,
    text    varchar,
    msg_time timestamp,
    visible Boolean,
    PRIMARY KEY (topic_id, msg_time, msg_id))
WITH CLUSTERING ORDER BY (msg_time DESC, msg_id DESC);