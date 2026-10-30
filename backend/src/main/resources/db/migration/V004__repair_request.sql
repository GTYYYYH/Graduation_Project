CREATE TABLE repair_request (
    id BIGINT NOT NULL PRIMARY KEY, created_at DATETIME(3) NOT NULL,
    request_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reporter_id BIGINT NOT NULL, equipment_name VARCHAR(100) NOT NULL, equipment_category VARCHAR(100) NULL,
    address_text VARCHAR(500) NOT NULL, description TEXT NOT NULL, contact_name VARCHAR(100) NOT NULL, contact_phone CHAR(11) NOT NULL,
    location_mode VARCHAR(16) NOT NULL, location_id BIGINT NULL, location_name_snapshot VARCHAR(500) NULL,
    status VARCHAR(32) NOT NULL, first_submitted_at DATETIME(3) NOT NULL,
    current_submission_id BIGINT NULL, matched_equipment_id BIGINT NULL,
    version INT NOT NULL DEFAULT 0, updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_request_no (request_no), KEY idx_request_reporter (reporter_id,status,created_at), KEY idx_request_location (location_id,status),
    FOREIGN KEY (reporter_id) REFERENCES user_account(id), FOREIGN KEY (location_id) REFERENCES campus_location(id), FOREIGN KEY (matched_equipment_id) REFERENCES equipment(id),
    CHECK (id>0), CHECK (version>=0), CHECK (CHAR_LENGTH(TRIM(equipment_name))>0), CHECK (CHAR_LENGTH(TRIM(address_text))>0),
    CHECK (CHAR_LENGTH(TRIM(description)) BETWEEN 1 AND 4000), CHECK (CHAR_LENGTH(TRIM(contact_name))>0), CHECK (REGEXP_LIKE(contact_phone,'^1[3-9][0-9]{9}$')),
    CHECK (status IN ('PENDING_REVIEW','NEEDS_SUPPLEMENT','APPROVED','WITHDRAWN','DUPLICATE_CLOSED')),
    CHECK ((location_mode='STRUCTURED' AND location_id IS NOT NULL AND location_name_snapshot IS NOT NULL) OR (location_mode='LEGACY_TEXT' AND location_id IS NULL AND location_name_snapshot IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE repair_request_submission (
    id BIGINT NOT NULL PRIMARY KEY, created_at DATETIME(3) NOT NULL,
    request_id BIGINT NOT NULL, submission_no INT NOT NULL, actor_user_id BIGINT NOT NULL,
    equipment_name VARCHAR(100) NOT NULL, equipment_category VARCHAR(100) NULL, address_text VARCHAR(500) NOT NULL, description TEXT NOT NULL,
    contact_name VARCHAR(100) NOT NULL, contact_phone CHAR(11) NOT NULL,
    location_mode VARCHAR(16) NOT NULL, location_id BIGINT NULL, location_name_snapshot VARCHAR(500) NULL, submitted_at DATETIME(3) NOT NULL,
    operation_type VARCHAR(32) NOT NULL, operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, content_digest BINARY(32) NOT NULL, result_version INT NOT NULL,
    UNIQUE KEY uk_request_submission (request_id,submission_no), UNIQUE KEY uk_submission_owner (request_id,id),
    UNIQUE KEY uk_submission_operation (actor_user_id,operation_type,operation_id),
    FOREIGN KEY (request_id) REFERENCES repair_request(id), FOREIGN KEY (actor_user_id) REFERENCES user_account(id), FOREIGN KEY (location_id) REFERENCES campus_location(id),
    CHECK (id>0), CHECK (submission_no>=1), CHECK (result_version>=0), CHECK (operation_type IN ('CREATE_REQUEST','SUPPLEMENT_REQUEST')),
    CHECK (CHAR_LENGTH(TRIM(equipment_name))>0), CHECK (CHAR_LENGTH(TRIM(address_text))>0), CHECK (CHAR_LENGTH(TRIM(description)) BETWEEN 1 AND 4000),
    CHECK (CHAR_LENGTH(TRIM(contact_name))>0), CHECK (REGEXP_LIKE(contact_phone,'^1[3-9][0-9]{9}$')),
    CHECK ((location_mode='STRUCTURED' AND location_id IS NOT NULL AND location_name_snapshot IS NOT NULL) OR (location_mode='LEGACY_TEXT' AND location_id IS NULL AND location_name_snapshot IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

ALTER TABLE repair_request ADD CONSTRAINT fk_request_current_submission FOREIGN KEY (id,current_submission_id) REFERENCES repair_request_submission(request_id,id);

CREATE TABLE repair_request_event (
    id BIGINT NOT NULL PRIMARY KEY, created_at DATETIME(3) NOT NULL,
    request_id BIGINT NOT NULL, actor_user_id BIGINT NOT NULL, submission_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL, from_status VARCHAR(32) NULL, to_status VARCHAR(32) NOT NULL, reason VARCHAR(2000) NULL, occurred_at DATETIME(3) NOT NULL,
    evidence_equipment_id BIGINT NULL, operation_type VARCHAR(32) NULL, operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL, content_digest BINARY(32) NULL,
    result_resource_type VARCHAR(32) NULL, result_resource_id BIGINT NULL, result_version INT NULL, result_round INT NULL,
    UNIQUE KEY uk_request_event_operation (actor_user_id,operation_type,operation_id), KEY idx_request_event (request_id,occurred_at,id),
    FOREIGN KEY (request_id) REFERENCES repair_request(id), FOREIGN KEY (actor_user_id) REFERENCES user_account(id), FOREIGN KEY (request_id,submission_id) REFERENCES repair_request_submission(request_id,id), FOREIGN KEY (evidence_equipment_id) REFERENCES equipment(id),
    CHECK (id>0), CHECK (result_version IS NULL OR result_version>=0), CHECK (result_round IS NULL OR result_round>=0),
    CHECK (event_type IN ('SUBMITTED','SUPPLEMENTED','RETURNED','WITHDRAWN','DUPLICATE_CLOSED','APPROVED_ASSIGNED')),
    CHECK (from_status IS NULL OR from_status IN ('PENDING_REVIEW','NEEDS_SUPPLEMENT','APPROVED','WITHDRAWN','DUPLICATE_CLOSED')),
    CHECK (to_status IN ('PENDING_REVIEW','NEEDS_SUPPLEMENT','APPROVED','WITHDRAWN','DUPLICATE_CLOSED')),
    CHECK (event_type NOT IN ('RETURNED','DUPLICATE_CLOSED') OR (reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason))>0)),
    CHECK (event_type<>'DUPLICATE_CLOSED' OR evidence_equipment_id IS NOT NULL),
    CHECK ((operation_type IS NULL AND operation_id IS NULL AND content_digest IS NULL) OR (operation_type IS NOT NULL AND operation_id IS NOT NULL AND content_digest IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

ALTER TABLE attachment_file
    ADD CONSTRAINT fk_attachment_request FOREIGN KEY (target_request_id) REFERENCES repair_request(id),
    ADD CONSTRAINT fk_attachment_submission FOREIGN KEY (bound_submission_id) REFERENCES repair_request_submission(id),
    ADD CONSTRAINT ck_attachment_submission_purpose CHECK (bound_submission_id IS NULL OR purpose IN ('REQUEST_FAULT','REQUEST_SUPPLEMENT'));
