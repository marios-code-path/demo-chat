DROP KEYSPACE IF EXISTS chat;

CREATE KEYSPACE chat WITH replication ={'class' : 'SimpleStrategy', 'replication_factor':1};
USE chat;

CREATE TYPE event_key_type(
    id UUID
);

CREATE TABLE topic_membership
(
    id       UUID,
    member   UUID,
    memberOf UUID,
    PRIMARY KEY (id)
);

CREATE TABLE topic_membership_by_member
(
    id       UUID,
    member   UUID,
    memberOf UUID,
    PRIMARY KEY (member)
);

CREATE TABLE topic_membership_by_member_of
(
    id       UUID,
    member   UUID,
    memberOf UUID,
    PRIMARY KEY (memberOf)
);