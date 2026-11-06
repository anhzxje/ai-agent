-- Apply manually after backup and deployment review; never run automatically by this application.
-- Additive relation. Existing chat and task data are unchanged.
CREATE TABLE chat_review_links (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    CONSTRAINT uk_chat_review_request UNIQUE (conversation_id, request_id),
    CONSTRAINT uk_chat_review_task UNIQUE (task_id),
    CONSTRAINT fk_chat_review_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_review_task FOREIGN KEY (task_id) REFERENCES agent_tasks(id) ON DELETE CASCADE
);
