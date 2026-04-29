-- ChatSession table
CREATE TABLE IF NOT EXISTS chat_session (
    session_id VARCHAR(64) PRIMARY KEY,
    session_name VARCHAR(255),
    host VARCHAR(255),
    port VARCHAR(16),
    url VARCHAR(512),
    username VARCHAR(128),
    password VARCHAR(256),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);