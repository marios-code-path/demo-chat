CREATE TABLE chat_room(
  room_id UUID,
  name varchar,
  active Boolean,
  PRIMARY KEY(room_id)
);

CREATE TABLE chat_room_name(
room_id UUID,
name varchar,
active Boolean,
PRIMARY KEY(name, room_id))
WITH CLUSTERING ORDER BY (room_id DESC);