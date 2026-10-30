CREATE TABLE user_account (
    id BIGINT NOT NULL PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    login_name VARCHAR(30) COLLATE utf8mb4_0900_as_ci NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone CHAR(11) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    review_status VARCHAR(16) NULL,
    review_attempt INT NOT NULL DEFAULT 0,
    provision_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    version INT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_user_login (login_name),
    UNIQUE KEY uk_user_phone (phone),
    UNIQUE KEY uk_user_provision (provision_key),
    CONSTRAINT ck_user_id CHECK (id > 0),
    CONSTRAINT ck_user_login CHECK (CHAR_LENGTH(login_name) BETWEEN 2 AND 30),
    CONSTRAINT ck_user_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_user_phone CHECK (REGEXP_LIKE(phone, '^1[3-9][0-9]{9}$')),
    CONSTRAINT ck_user_role CHECK (role IN ('REPORTER', 'TECHNICIAN', 'ADMIN')),
    CONSTRAINT ck_user_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_user_version CHECK (version >= 0),
    CONSTRAINT ck_user_review CHECK (
        (role = 'TECHNICIAN' AND review_status IS NOT NULL AND review_status IN ('PENDING','REJECTED','APPROVED') AND review_attempt >= 1)
        OR (role IN ('REPORTER','ADMIN') AND review_status IS NULL AND review_attempt = 0)
    ),
    CONSTRAINT ck_user_provision CHECK (provision_key IS NULL OR role = 'ADMIN')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE login_session (
    id BIGINT NOT NULL PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    user_id BIGINT NOT NULL,
    token_digest BINARY(32) NOT NULL,
    issued_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    revoke_reason VARCHAR(32) NULL,
    UNIQUE KEY uk_session_digest (token_digest),
    KEY idx_session_user (user_id),
    KEY idx_session_expiry (expires_at),
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT ck_session_id CHECK (id > 0),
    CONSTRAINT ck_session_expiry CHECK (expires_at > issued_at),
    CONSTRAINT ck_session_reason CHECK (revoke_reason IS NULL OR revoke_reason IN ('LOGOUT','PASSWORD_CHANGED','PASSWORD_RESET','ACCOUNT_DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE registration_review (
    id BIGINT NOT NULL PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    user_id BIGINT NOT NULL,
    attempt INT NOT NULL,
    name_snapshot VARCHAR(100) NOT NULL,
    phone_snapshot CHAR(11) NOT NULL,
    submitted_at DATETIME(3) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(3) NULL,
    reason VARCHAR(2000) NULL,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_review_attempt (user_id, attempt),
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_review_actor FOREIGN KEY (reviewed_by) REFERENCES user_account(id),
    CONSTRAINT ck_review_id CHECK (id > 0),
    CONSTRAINT ck_review_attempt CHECK (attempt >= 1),
    CONSTRAINT ck_review_version CHECK (version >= 0),
    CONSTRAINT ck_review_name CHECK (CHAR_LENGTH(TRIM(name_snapshot)) > 0),
    CONSTRAINT ck_review_phone CHECK (REGEXP_LIKE(phone_snapshot, '^1[3-9][0-9]{9}$')),
    CONSTRAINT ck_review_status CHECK (status IN ('PENDING','REJECTED','APPROVED')),
    CONSTRAINT ck_review_decision CHECK (
        (status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR (status IN ('REJECTED','APPROVED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT ck_review_reason CHECK (status <> 'REJECTED' OR (reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason)) > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE account_event (
    id BIGINT NOT NULL PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    review_id BIGINT NULL,
    reason VARCHAR(2000) NULL,
    operation_type VARCHAR(32) NULL,
    operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    content_digest BINARY(32) NULL,
    result_version INT NULL,
    result_resource_id BIGINT NULL,
    UNIQUE KEY uk_account_operation (actor_user_id, operation_type, operation_id),
    KEY idx_account_event_target (target_user_id, occurred_at, id),
    CONSTRAINT fk_account_event_actor FOREIGN KEY (actor_user_id) REFERENCES user_account(id),
    CONSTRAINT fk_account_event_target FOREIGN KEY (target_user_id) REFERENCES user_account(id),
    CONSTRAINT fk_account_event_review FOREIGN KEY (review_id) REFERENCES registration_review(id),
    CONSTRAINT ck_account_event_id CHECK (id > 0),
    CONSTRAINT ck_account_event_type CHECK (event_type IN ('PROFILE_UPDATED','PASSWORD_CHANGED','PASSWORD_RESET','ENABLED','DISABLED','REVIEW_RESUBMITTED','REVIEW_DECIDED')),
    CONSTRAINT ck_account_operation CHECK (
        (operation_type IS NULL AND operation_id IS NULL AND content_digest IS NULL)
        OR (operation_type IS NOT NULL AND operation_id IS NOT NULL AND content_digest IS NOT NULL)
    ),
    CONSTRAINT ck_account_result_version CHECK (result_version IS NULL OR result_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
