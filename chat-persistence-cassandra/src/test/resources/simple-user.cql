DROP TABLE IF EXISTS chat_user;
DROP TABLE IF EXISTS chat_user_handle;

CREATE TABLE chat.chat_user(
user_id UUID,
handle varchar,
name varchar,
image_uri varchar,
timestamp timestamp,
PRIMARY KEY(user_id));

CREATE TABLE chat.chat_user_handle(
user_id UUID,
handle varchar,
name varchar,
image_uri varchar,
timestamp timestamp,
PRIMARY KEY(handle));