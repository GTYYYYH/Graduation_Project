CREATE TABLE campus_building (
    id BIGINT NOT NULL PRIMARY KEY, created_at DATETIME(3) NOT NULL,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(100) NOT NULL, type VARCHAR(16) NOT NULL,
    display_order INT NOT NULL, config_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    UNIQUE KEY uk_building_code (code),
    CHECK (id>0), CHECK (CHAR_LENGTH(TRIM(name))>0), CHECK (display_order>=0),
    CHECK (type IN ('DORMITORY','TEACHING','CANTEEN','GYM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE campus_floor (
    id BIGINT NOT NULL PRIMARY KEY, created_at DATETIME(3) NOT NULL,
    building_id BIGINT NOT NULL, level INT NOT NULL,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(100) NOT NULL, display_order INT NOT NULL,
    config_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    UNIQUE KEY uk_floor_code (code), UNIQUE KEY uk_floor_level (building_id,level),
    FOREIGN KEY (building_id) REFERENCES campus_building(id),
    CHECK (id>0), CHECK (level>=1), CHECK (display_order>=0), CHECK (CHAR_LENGTH(TRIM(name))>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE campus_location (
    id BIGINT NOT NULL PRIMARY KEY, created_at DATETIME(3) NOT NULL, floor_id BIGINT NOT NULL,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    type VARCHAR(16) NOT NULL, space_use VARCHAR(32) NOT NULL, name VARCHAR(100) NOT NULL,
    reportable BOOLEAN NOT NULL, display_order INT NOT NULL,
    config_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version INT NOT NULL DEFAULT 0, updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_location_code (code), KEY idx_location_floor (floor_id,display_order),
    FOREIGN KEY (floor_id) REFERENCES campus_floor(id),
    CHECK (id>0), CHECK (version>=0), CHECK (display_order>=0), CHECK (reportable IN (0,1)),
    CHECK (CHAR_LENGTH(TRIM(name))>0), CHECK (type IN ('ROOM','AREA')),
    CHECK (space_use IN ('DORM_ROOM','DORM_LOBBY','DORM_DUTY','CLASSROOM','OFFICE','TEACHING_LOBBY','RESTROOM_MALE','RESTROOM_FEMALE','CANTEEN_AREA','GYM_COURT','GYM_GALLERY')),
    CHECK ((space_use IN ('DORM_ROOM','CLASSROOM','OFFICE','CANTEEN_AREA','GYM_COURT','GYM_GALLERY') AND reportable=1)
        OR (space_use IN ('DORM_LOBBY','DORM_DUTY','TEACHING_LOBBY','RESTROOM_MALE','RESTROOM_FEMALE') AND reportable=0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE equipment (
    id BIGINT NOT NULL PRIMARY KEY, created_at DATETIME(3) NOT NULL,
    equipment_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(100) NOT NULL, location_id BIGINT NULL, address_text VARCHAR(500) NOT NULL,
    facility_type VARCHAR(32) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE,
    initialization_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    version INT NOT NULL DEFAULT 0, updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_equipment_no (equipment_no), UNIQUE KEY uk_equipment_initialization (initialization_key),
    KEY idx_equipment_location (location_id,enabled), FOREIGN KEY (location_id) REFERENCES campus_location(id),
    CHECK (id>0), CHECK (version>=0), CHECK (enabled IN (0,1)), CHECK (CHAR_LENGTH(TRIM(name))>0),
    CHECK (CHAR_LENGTH(TRIM(address_text))>0),
    CHECK (facility_type IN ('BUNK_DESK','TOILET','SHOWER','BASIN','AIR_CONDITIONER','AIR_CONDITIONER_GROUP','LIGHT_GROUP','SOCKET_GROUP','CLASS_DESK_CHAIR_GROUP','COMPUTER','PROJECTOR','OFFICE_DESK_CHAIR_GROUP','STANDING_AC_GROUP','BASKETBALL_HOOP_GROUP'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE equipment_event (
    id BIGINT NOT NULL PRIMARY KEY, created_at DATETIME(3) NOT NULL, equipment_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL, event_type VARCHAR(32) NOT NULL, occurred_at DATETIME(3) NOT NULL,
    operation_type VARCHAR(32) NOT NULL, operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_digest BINARY(32) NOT NULL, result_version INT NOT NULL,
    UNIQUE KEY uk_equipment_operation (actor_user_id,operation_type,operation_id),
    KEY idx_equipment_event (equipment_id,occurred_at,id),
    FOREIGN KEY (equipment_id) REFERENCES equipment(id), FOREIGN KEY (actor_user_id) REFERENCES user_account(id),
    CHECK (id>0), CHECK (result_version>=0), CHECK (event_type IN ('CREATED','UPDATED','ENABLED','DISABLED','LOCATION_CHANGED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE equipment_location_history (
    id BIGINT NOT NULL PRIMARY KEY, created_at DATETIME(3) NOT NULL,
    equipment_id BIGINT NOT NULL, actor_user_id BIGINT NOT NULL, old_location_id BIGINT NULL, new_location_id BIGINT NULL,
    old_address_text VARCHAR(500) NOT NULL, new_address_text VARCHAR(500) NOT NULL,
    reason VARCHAR(2000) NOT NULL, changed_at DATETIME(3) NOT NULL, equipment_event_id BIGINT NOT NULL,
    KEY idx_equipment_location_history (equipment_id,changed_at,id),
    FOREIGN KEY (equipment_id) REFERENCES equipment(id), FOREIGN KEY (actor_user_id) REFERENCES user_account(id),
    FOREIGN KEY (old_location_id) REFERENCES campus_location(id), FOREIGN KEY (new_location_id) REFERENCES campus_location(id),
    FOREIGN KEY (equipment_event_id) REFERENCES equipment_event(id), CHECK (id>0), CHECK (CHAR_LENGTH(TRIM(reason))>0),
    CHECK (NOT (old_location_id <=> new_location_id))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE location_name_history (
    id BIGINT NOT NULL PRIMARY KEY, created_at DATETIME(3) NOT NULL, location_id BIGINT NOT NULL,
    operator_label VARCHAR(100) NOT NULL, old_name VARCHAR(100) NOT NULL, new_name VARCHAR(100) NOT NULL,
    reason VARCHAR(2000) NOT NULL, changed_at DATETIME(3) NOT NULL,
    KEY idx_location_name_history (location_id,changed_at,id), FOREIGN KEY (location_id) REFERENCES campus_location(id),
    CHECK (id>0), CHECK (old_name<>new_name), CHECK (CHAR_LENGTH(TRIM(reason))>0), CHECK (CHAR_LENGTH(TRIM(operator_label))>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE campus_initialization (
    id BIGINT NOT NULL PRIMARY KEY, created_at DATETIME(3) NOT NULL,
    config_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, config_digest BINARY(32) NOT NULL,
    building_count INT NOT NULL, floor_count INT NOT NULL, location_count INT NOT NULL,
    reportable_count INT NOT NULL, equipment_count INT NOT NULL, initialized_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_campus_initialization (config_version), CHECK (id>0),
    CHECK (building_count>=0 AND floor_count>=0 AND location_count>=0 AND reportable_count>=0 AND equipment_count>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
