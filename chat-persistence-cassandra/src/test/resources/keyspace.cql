DROP KEYSPACE IF EXISTS chat;

CREATE KEYSPACE IF NOT EXISTS chat WITH replication={'class' : 'SimpleStrategy', 'replication_factor':'1'};