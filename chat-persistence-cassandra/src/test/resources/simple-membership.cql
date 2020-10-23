DROP TABLE IF EXISTS chat.topic_membership;
DROP TABLE IF EXISTS chat.topic_membership_by_member;
DROP TABLE IF EXISTS chat.topic_membership_by_member_of;

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