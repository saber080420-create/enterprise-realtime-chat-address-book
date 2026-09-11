-- Run explicitly against the existing MySQL business database. No existing tables are modified.
CREATE TABLE IF NOT EXISTS ai_document (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_document_owner (owner_id, status, id)
);
