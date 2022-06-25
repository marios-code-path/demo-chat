DROP KEYSPACE IF EXISTS chat;
CREATE KEYSPACE IF NOT EXISTS chat WITH replication={'class' : 'SimpleStrategy', 'replication_factor':'1'};

CREATE TYPE chat.event_key_meta(
    kind varchar
);

CREATE TABLE chat.keys(
                          id UUID,
                          kind varchar ,
                          PRIMARY KEY(id) );


CREATE TABLE chat.topic_membership
(
    id       UUID,
    member   UUID,
    memberOf UUID,
    PRIMARY KEY (id)
);

CREATE TABLE chat.topic_membership_by_member
(
    id       UUID,
    member   UUID,
    memberOf UUID,
    PRIMARY KEY (member)
);

CREATE TABLE chat.topic_membership_by_member_of
(
    id       UUID,
    member   UUID,
    memberOf UUID,
    PRIMARY KEY (memberOf)
);


CREATE TABLE chat.chat_message_id (
                                      msg_id TIMEUUID,
                                      user_id UUID,
                                      topic_id UUID,
                                      text    varchar,
                                      msg_time timestamp,
                                      visible Boolean,
                                      PRIMARY KEY (msg_id, msg_time))
    WITH CLUSTERING ORDER BY (msg_time DESC);

CREATE TABLE chat.chat_message_user (
                                        msg_id TIMEUUID,
                                        user_id UUID,
                                        topic_id UUID,
                                        text    varchar,
                                        msg_time timestamp,
                                        visible Boolean,
                                        PRIMARY KEY (user_id, msg_time, msg_id))
    WITH CLUSTERING ORDER BY (msg_time DESC, msg_id DESC);

CREATE TABLE chat.chat_message_topic (
                                         msg_id TIMEUUID,
                                         user_id UUID,
                                         topic_id UUID,
                                         text    varchar,
                                         msg_time timestamp,
                                         visible Boolean,
                                         PRIMARY KEY (topic_id, msg_time, msg_id))
    WITH CLUSTERING ORDER BY (msg_time DESC, msg_id DESC);


CREATE TABLE chat.chat_room(
                               room_id UUID,
                               name varchar,
                               active Boolean,
                               PRIMARY KEY(room_id)
);

CREATE TABLE chat.chat_room_name(
                                    room_id UUID,
                                    name varchar,
                                    active Boolean,
                                    PRIMARY KEY(name, room_id))
    WITH CLUSTERING ORDER BY (room_id DESC);


CREATE TABLE chat.chat_user
(
    user_id   UUID,
    handle    varchar,
    name      varchar,
    image_uri varchar,
    timestamp timestamp,
    PRIMARY KEY (user_id)
);

CREATE TABLE chat.chat_user_handle
(
    user_id   UUID,
    handle    varchar,
    name      varchar,
    image_uri varchar,
    timestamp timestamp,
    PRIMARY KEY (handle)
);