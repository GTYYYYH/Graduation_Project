CREATE TABLE attachment_file (
    id BIGINT NOT NULL PRIMARY KEY, created_at DATETIME(3) NOT NULL,
    uploader_id BIGINT NOT NULL, upload_operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    purpose VARCHAR(32) NOT NULL, state VARCHAR(16) NOT NULL,
    batch_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    target_request_id BIGINT NULL, target_work_order_id BIGINT NULL, target_round INT NULL,
    original_name VARCHAR(255) NOT NULL, storage_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    media_type VARCHAR(32) NOT NULL, byte_size BIGINT NOT NULL, file_digest BINARY(32) NOT NULL,
    uploaded_at DATETIME(3) NOT NULL, expires_at DATETIME(3) NOT NULL,
    bound_submission_id BIGINT NULL, bound_round_id BIGINT NULL, bound_at DATETIME(3) NULL,
    deletion_claimed_at DATETIME(3) NULL, deleted_at DATETIME(3) NULL, delete_attempts INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_upload (uploader_id,upload_operation_id), UNIQUE KEY uk_attachment_storage (storage_key),
    KEY idx_attachment_expiry (state,expires_at), KEY idx_attachment_submission (bound_submission_id), KEY idx_attachment_round (bound_round_id),
    CONSTRAINT fk_attachment_uploader FOREIGN KEY (uploader_id) REFERENCES user_account(id),
    CHECK (id>0), CHECK (byte_size BETWEEN 1 AND 5000000), CHECK (delete_attempts>=0),
    CHECK (media_type IN ('image/jpeg','image/png')), CHECK (expires_at>uploaded_at),
    CHECK (CHAR_LENGTH(original_name)>0),
    CHECK (purpose IN ('REQUEST_FAULT','REQUEST_SUPPLEMENT','REPAIR_VOUCHER')),
    CHECK (state IN ('TEMPORARY','BOUND','DELETING','DELETED')),
    CONSTRAINT ck_attachment_context CHECK (
        (purpose='REQUEST_FAULT' AND batch_id IS NOT NULL AND target_request_id IS NULL AND target_work_order_id IS NULL AND target_round IS NULL)
        OR (purpose='REQUEST_SUPPLEMENT' AND batch_id IS NULL AND target_request_id IS NOT NULL AND target_work_order_id IS NULL AND target_round IS NULL)
        OR (purpose='REPAIR_VOUCHER' AND batch_id IS NULL AND target_request_id IS NULL AND target_work_order_id IS NOT NULL AND target_round IS NOT NULL AND target_round>=1)
    ),
    CONSTRAINT ck_attachment_binding CHECK (
        (state='BOUND' AND bound_at IS NOT NULL AND ((bound_submission_id IS NOT NULL AND bound_round_id IS NULL) OR (bound_submission_id IS NULL AND bound_round_id IS NOT NULL)))
        OR (state IN ('TEMPORARY','DELETING','DELETED') AND bound_at IS NULL AND bound_submission_id IS NULL AND bound_round_id IS NULL)
    ),
    CHECK ((state='DELETING' AND deletion_claimed_at IS NOT NULL AND deleted_at IS NULL)
        OR (state='DELETED' AND deletion_claimed_at IS NOT NULL AND deleted_at IS NOT NULL)
        OR (state IN ('TEMPORARY','BOUND') AND deletion_claimed_at IS NULL AND deleted_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
