CREATE TABLE work_order (
    id BIGINT NOT NULL PRIMARY KEY, created_at DATETIME(3) NOT NULL,
    work_order_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_id BIGINT NOT NULL, equipment_id BIGINT NOT NULL, reporter_id BIGINT NOT NULL,
    location_mode VARCHAR(16) NOT NULL, location_id BIGINT NULL, location_name_snapshot VARCHAR(500) NULL,
    status VARCHAR(32) NOT NULL, version INT NOT NULL DEFAULT 0, current_round INT NOT NULL DEFAULT 0, updated_at DATETIME(3) NOT NULL,
    current_assignment_id BIGINT NULL, assigned_technician_id BIGINT NULL, accepted_technician_id BIGINT NULL,
    accepted_at DATETIME(3) NULL, completed_at DATETIME(3) NULL,
    unfinished_equipment_id BIGINT GENERATED ALWAYS AS (CASE WHEN status<>'COMPLETED' THEN equipment_id ELSE NULL END) STORED,
    UNIQUE KEY uk_order_no(work_order_no), UNIQUE KEY uk_order_request(request_id), UNIQUE KEY uk_order_unfinished_equipment(unfinished_equipment_id),
    KEY idx_order_assignee(assigned_technician_id,status), KEY idx_order_reporter(reporter_id,status), KEY idx_order_location(location_id,status), KEY idx_order_equipment(equipment_id,created_at),
    FOREIGN KEY(request_id) REFERENCES repair_request(id), FOREIGN KEY(equipment_id) REFERENCES equipment(id), FOREIGN KEY(reporter_id) REFERENCES user_account(id), FOREIGN KEY(location_id) REFERENCES campus_location(id),
    FOREIGN KEY(assigned_technician_id) REFERENCES user_account(id), FOREIGN KEY(accepted_technician_id) REFERENCES user_account(id),
    CHECK(id>0), CHECK(version>=0), CHECK(current_round>=0), CHECK(status IN ('PENDING_ACCEPT','REASSIGN_PENDING','IN_PROGRESS','PENDING_ACCEPTANCE','COMPLETED')),
    CHECK((location_mode='STRUCTURED' AND location_id IS NOT NULL AND location_name_snapshot IS NOT NULL) OR (location_mode='LEGACY_TEXT' AND location_id IS NULL AND location_name_snapshot IS NULL)),
    CHECK((status IN ('PENDING_ACCEPT','REASSIGN_PENDING') AND current_round=0 AND accepted_technician_id IS NULL AND accepted_at IS NULL)
      OR (status IN ('IN_PROGRESS','PENDING_ACCEPTANCE','COMPLETED') AND current_round>=1 AND accepted_technician_id IS NOT NULL AND accepted_at IS NOT NULL AND assigned_technician_id IS NOT NULL AND assigned_technician_id=accepted_technician_id)),
    CHECK((status='REASSIGN_PENDING' AND current_assignment_id IS NULL AND assigned_technician_id IS NULL) OR (status<>'REASSIGN_PENDING' AND assigned_technician_id IS NOT NULL)),
    CHECK((status='COMPLETED' AND completed_at IS NOT NULL AND completed_at>=accepted_at) OR (status<>'COMPLETED' AND completed_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE work_order_assignment (
    id BIGINT NOT NULL PRIMARY KEY, created_at DATETIME(3) NOT NULL, work_order_id BIGINT NOT NULL,
    assignment_no INT NOT NULL, technician_id BIGINT NOT NULL, assigned_by BIGINT NOT NULL, visible_submission_id BIGINT NOT NULL,
    assigned_at DATETIME(3) NOT NULL, ended_at DATETIME(3) NULL, outcome VARCHAR(16) NOT NULL, reason VARCHAR(2000) NULL, decision_at DATETIME(3) NULL,
    UNIQUE KEY uk_assignment_no(work_order_id,assignment_no), UNIQUE KEY uk_assignment_owner(work_order_id,id), KEY idx_assignment_technician(technician_id,assigned_at),
    FOREIGN KEY(work_order_id) REFERENCES work_order(id), FOREIGN KEY(technician_id) REFERENCES user_account(id), FOREIGN KEY(assigned_by) REFERENCES user_account(id), FOREIGN KEY(visible_submission_id) REFERENCES repair_request_submission(id),
    CHECK(id>0), CHECK(assignment_no>=1), CHECK(outcome IN ('PENDING','ACCEPTED','REFUSED','REPLACED')),
    CHECK(ended_at IS NULL OR ended_at>=assigned_at), CHECK(decision_at IS NULL OR decision_at>=assigned_at),
    CHECK((outcome='PENDING' AND ended_at IS NULL AND decision_at IS NULL AND reason IS NULL) OR (outcome='ACCEPTED' AND decision_at IS NOT NULL)
      OR (outcome IN ('REFUSED','REPLACED') AND ended_at IS NOT NULL AND decision_at IS NOT NULL AND reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason))>0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

ALTER TABLE work_order ADD CONSTRAINT fk_order_current_assignment FOREIGN KEY(id,current_assignment_id) REFERENCES work_order_assignment(work_order_id,id);

CREATE TABLE repair_round (
    id BIGINT NOT NULL PRIMARY KEY, created_at DATETIME(3) NOT NULL, work_order_id BIGINT NOT NULL, round_no INT NOT NULL, technician_id BIGINT NOT NULL,
    started_at DATETIME(3) NOT NULL, submitted_at DATETIME(3) NULL, result_text TEXT NULL,
    acceptance_passed BOOLEAN NULL, acceptance_reason VARCHAR(2000) NULL, accepted_by BIGINT NULL, acceptance_at DATETIME(3) NULL,
    UNIQUE KEY uk_round_no(work_order_id,round_no), UNIQUE KEY uk_round_owner(work_order_id,id), UNIQUE KEY uk_round_target(work_order_id,round_no,id),
    FOREIGN KEY(work_order_id) REFERENCES work_order(id), FOREIGN KEY(technician_id) REFERENCES user_account(id), FOREIGN KEY(accepted_by) REFERENCES user_account(id),
    CHECK(id>0), CHECK(round_no>=1), CHECK(acceptance_passed IS NULL OR acceptance_passed IN (0,1)),
    CHECK((submitted_at IS NULL AND result_text IS NULL AND acceptance_passed IS NULL AND acceptance_reason IS NULL AND accepted_by IS NULL AND acceptance_at IS NULL)
      OR (submitted_at IS NOT NULL AND submitted_at>=started_at AND result_text IS NOT NULL AND CHAR_LENGTH(TRIM(result_text)) BETWEEN 1 AND 4000)),
    CHECK((acceptance_at IS NULL AND acceptance_passed IS NULL AND acceptance_reason IS NULL AND accepted_by IS NULL)
      OR (acceptance_at IS NOT NULL AND submitted_at IS NOT NULL AND acceptance_at>=submitted_at AND acceptance_passed IS NOT NULL AND accepted_by IS NOT NULL AND (acceptance_passed=1 OR (acceptance_reason IS NOT NULL AND CHAR_LENGTH(TRIM(acceptance_reason))>0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE repair_progress (
    id BIGINT NOT NULL PRIMARY KEY, created_at DATETIME(3) NOT NULL, work_order_id BIGINT NOT NULL, round_id BIGINT NOT NULL, technician_id BIGINT NOT NULL, content TEXT NOT NULL, recorded_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_progress_owner(work_order_id,id), KEY idx_progress_round(round_id,recorded_at,id),
    FOREIGN KEY(work_order_id) REFERENCES work_order(id), FOREIGN KEY(work_order_id,round_id) REFERENCES repair_round(work_order_id,id), FOREIGN KEY(technician_id) REFERENCES user_account(id),
    CHECK(id>0), CHECK(CHAR_LENGTH(TRIM(content)) BETWEEN 1 AND 4000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE work_order_event (
    id BIGINT NOT NULL PRIMARY KEY, created_at DATETIME(3) NOT NULL, work_order_id BIGINT NOT NULL, actor_user_id BIGINT NOT NULL,
    assignment_id BIGINT NULL, round_id BIGINT NULL, progress_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL, from_status VARCHAR(32) NULL, to_status VARCHAR(32) NOT NULL, reason VARCHAR(2000) NULL, occurred_at DATETIME(3) NOT NULL,
    operation_type VARCHAR(32) NULL, operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL, content_digest BINARY(32) NULL,
    result_resource_id BIGINT NULL, result_version INT NULL, result_round INT NULL,
    UNIQUE KEY uk_order_event_operation(actor_user_id,operation_type,operation_id), KEY idx_order_event(work_order_id,occurred_at,id),
    FOREIGN KEY(work_order_id) REFERENCES work_order(id), FOREIGN KEY(actor_user_id) REFERENCES user_account(id), FOREIGN KEY(work_order_id,assignment_id) REFERENCES work_order_assignment(work_order_id,id),
    FOREIGN KEY(work_order_id,round_id) REFERENCES repair_round(work_order_id,id), FOREIGN KEY(work_order_id,progress_id) REFERENCES repair_progress(work_order_id,id),
    CHECK(id>0), CHECK(result_version IS NULL OR result_version>=0), CHECK(result_round IS NULL OR result_round>=0),
    CHECK(event_type IN ('FIRST_ASSIGNED','ACCEPTED','REFUSED','ASSIGNED','PROGRESS_ADDED','RESULT_SUBMITTED','ACCEPTANCE_FAILED','COMPLETED')),
    CHECK(from_status IS NULL OR from_status IN ('PENDING_ACCEPT','REASSIGN_PENDING','IN_PROGRESS','PENDING_ACCEPTANCE','COMPLETED')),
    CHECK(to_status IN ('PENDING_ACCEPT','REASSIGN_PENDING','IN_PROGRESS','PENDING_ACCEPTANCE','COMPLETED')),
    CHECK((operation_type IS NULL AND operation_id IS NULL AND content_digest IS NULL) OR (operation_type IS NOT NULL AND operation_id IS NOT NULL AND content_digest IS NOT NULL)),
    CHECK(event_type NOT IN ('REFUSED','ASSIGNED','ACCEPTANCE_FAILED') OR (reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason))>0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

ALTER TABLE attachment_file
    ADD CONSTRAINT fk_attachment_order FOREIGN KEY(target_work_order_id) REFERENCES work_order(id),
    ADD CONSTRAINT fk_attachment_round FOREIGN KEY(bound_round_id) REFERENCES repair_round(id),
    ADD CONSTRAINT fk_attachment_round_target FOREIGN KEY(target_work_order_id,target_round,bound_round_id) REFERENCES repair_round(work_order_id,round_no,id),
    ADD CONSTRAINT ck_attachment_round_purpose CHECK(bound_round_id IS NULL OR purpose='REPAIR_VOUCHER');
