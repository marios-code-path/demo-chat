CREATE TABLE oauth2_client_assertion_jti (
    client_id varchar(100) NOT NULL,
    jti varchar(200) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    PRIMARY KEY (client_id, jti)
);

CREATE INDEX oauth2_client_assertion_jti_expires_at_index
    ON oauth2_client_assertion_jti (expires_at);
