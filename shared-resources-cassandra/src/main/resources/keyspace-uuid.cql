DROP KEYSPACE IF EXISTS chat_uuid;
CREATE KEYSPACE IF NOT EXISTS chat_uuid WITH replication={'class' : 'SimpleStrategy', 'replication_factor':'1'};

CREATE TYPE chat_uuid.event_key_meta(
    kind varchar
);

CREATE TABLE chat_uuid.keys(
                          id UUID,
                          kind varchar ,
                          PRIMARY KEY(id) );


CREATE TABLE chat_uuid.topic_membership
(
    id       UUID,
    member   UUID,
    member_of UUID,
    PRIMARY KEY (id)
);

CREATE TABLE chat_uuid.topic_membership_by_member
(
    id       UUID,
    member   UUID,
    member_of UUID,
    PRIMARY KEY (member)
);

CREATE TABLE chat_uuid.topic_membership_by_member_of
(
    id       UUID,
    member   UUID,
    member_of UUID,
    PRIMARY KEY (member_of)
);

CREATE TABLE chat_uuid.chat_secret (
                                       id UUID,
                                       data varchar,
                                       kind varchar,
                                       PRIMARY KEY (id)
);


CREATE TABLE chat_uuid.chat_message_id (
                                      msg_id TIMEUUID,
                                      user_id UUID,
                                      topic_id UUID,
                                      text    varchar,
                                      msg_time timestamp,
                                      visible Boolean,
                                      PRIMARY KEY (msg_id, msg_time))
    WITH CLUSTERING ORDER BY (msg_time DESC);

CREATE TABLE chat_uuid.chat_message_user (
                                        msg_id TIMEUUID,
                                        user_id UUID,
                                        topic_id UUID,
                                        text    varchar,
                                        msg_time timestamp,
                                        visible Boolean,
                                        PRIMARY KEY (user_id, msg_time, msg_id))
    WITH CLUSTERING ORDER BY (msg_time DESC, msg_id DESC);

CREATE TABLE chat_uuid.chat_message_topic (
                                         msg_id TIMEUUID,
                                         user_id UUID,
                                         topic_id UUID,
                                         text    varchar,
                                         msg_time timestamp,
                                         visible Boolean,
                                         PRIMARY KEY (topic_id, msg_time, msg_id))
    WITH CLUSTERING ORDER BY (msg_time DESC, msg_id DESC);


CREATE TABLE chat_uuid.chat_room(
                               room_id UUID,
                               name varchar,
                               active Boolean,
                               PRIMARY KEY(room_id)
);

CREATE TABLE chat_uuid.chat_room_name(
                                    room_id UUID,
                                    name varchar,
                                    active Boolean,
                                    PRIMARY KEY(name, room_id))
    WITH CLUSTERING ORDER BY (room_id DESC);


CREATE TABLE chat_uuid.chat_user
(
    user_id   UUID,
    handle    varchar,
    name      varchar,
    image_uri varchar,
    timestamp timestamp,
    PRIMARY KEY (user_id)
);

CREATE TABLE chat_uuid.chat_user_handle
(
    user_id   UUID,
    handle    varchar,
    name      varchar,
    image_uri varchar,
    timestamp timestamp,
    PRIMARY KEY (handle)
);

CREATE TABLE chat_uuid.auth_metadata
(
    id  UUID,
    principal   UUID,
    target      UUID,
    permission  varchar,
    mute        Boolean,
    expires bigint,
    PRIMARY KEY (id)
);

CREATE TABLE chat_uuid.auth_metadata_principal
(
    id  UUID,
    principal   UUID,
    target      UUID,
    permission  varchar,
    mute        Boolean,
    expires bigint,
    PRIMARY KEY (principal)
);

CREATE TABLE chat_uuid.auth_metadata_target
(
    id  UUID,
    principal   UUID,
    target      UUID,
    permission  varchar,
    mute        Boolean,
    expires bigint,
    PRIMARY KEY (target)
);