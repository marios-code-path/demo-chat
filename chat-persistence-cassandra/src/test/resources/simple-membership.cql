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