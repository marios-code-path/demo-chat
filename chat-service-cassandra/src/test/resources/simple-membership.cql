DROP KEYSPACE IF EXISTS chat;

CREATE KEYSPACE chat WITH replication ={'class' : 'SimpleStrategy', 'replication_factor':1};
USE chat;

CREATE TYPE event_key_type(
    id UUID
);

CREATE TABLE chat_membership
(
    id       UUID,
    member   frozen<event_key_type>,
    memberOf frozen<event_key_type>,
    PRIMARY KEY (id)
);

CREATE TABLE chat_membership_by_member
(
    id       frozen<event_key_type>,
    member   UUID,
    memberOf frozen<event_key_type>,
    PRIMARY KEY (member)
);

CREATE TABLE chat_membership_by_memberof
(
    id       frozen<event_key_type>,
    member   frozen<event_key_type>,
    memberOf UUID,
    PRIMARY KEY (memberOf)
);