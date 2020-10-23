DROP TABLE IF EXISTS chat.keys;
DROP TYPE IF EXISTS chat.event_key_meta;

CREATE TYPE chat.event_key_meta(
    kind varchar
);

CREATE TABLE chat.keys(
id UUID,
kind varchar ,
PRIMARY KEY(id) );