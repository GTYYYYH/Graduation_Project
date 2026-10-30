-- Normal first assignment may use only the reported material and chosen technician.
-- Existing links, foreign keys and uniqueness for explicitly linked equipment remain intact.
ALTER TABLE work_order MODIFY COLUMN equipment_id BIGINT NULL;
