package cn.wzbc.maintenance.campus;

import cn.wzbc.maintenance.Application;
import cn.wzbc.maintenance.campus.service.CampusInitializationService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = Application.class)
@Transactional
class CampusInitializationIT {
    @Autowired CampusInitializationService service;
    @Autowired JdbcTemplate db;
    final Path config = Path.of("../config/campus.json").toAbsolutePath().normalize();
    int count(String table) { return db.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    @Test void initial_counts_are_exact() {
        service.initialize(config);
        assertEquals(10, count("campus_building"));
        assertEquals(41, count("campus_floor"));
        assertEquals(296, count("campus_location"));
        assertEquals(257, db.queryForObject("SELECT COUNT(*) FROM campus_location WHERE reportable=1", Integer.class));
        int equipmentCount = count("equipment");
        assertEquals(1817, equipmentCount);
        assertEquals(1, count("campus_initialization"));
        for (var expected : java.util.Map.of("DORMITORY",1392,"TEACHING",402,"CANTEEN",18,"GYM",5).entrySet()) {
            assertEquals(expected.getValue(), db.queryForObject("SELECT COUNT(*) FROM equipment e JOIN campus_location l ON e.location_id=l.id JOIN campus_floor f ON l.floor_id=f.id JOIN campus_building b ON f.building_id=b.id WHERE b.type=?", Integer.class, expected.getKey()));
        }
        assertEquals(174, db.queryForObject("SELECT COUNT(*) FROM campus_location WHERE space_use='DORM_ROOM'", Integer.class));
        assertEquals(57, db.queryForObject("SELECT COUNT(*) FROM campus_location WHERE space_use='CLASSROOM'", Integer.class));
        assertEquals(15, db.queryForObject("SELECT COUNT(*) FROM campus_location WHERE space_use='OFFICE'", Integer.class));
        assertEquals(9, db.queryForObject("SELECT COUNT(*) FROM campus_location WHERE space_use='CANTEEN_AREA'", Integer.class));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM campus_location WHERE space_use IN ('GYM_COURT','GYM_GALLERY')", Integer.class));
        assertEquals(72, db.queryForObject("SELECT COUNT(*) FROM equipment e JOIN campus_location l ON e.location_id=l.id WHERE l.space_use IN ('CLASSROOM','OFFICE') AND e.facility_type IN ('AIR_CONDITIONER','AIR_CONDITIONER_GROUP')", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM equipment e JOIN campus_location l ON e.location_id=l.id WHERE l.reportable=0", Integer.class));
        // 独立人工规则核对每个位置，不仅总数。
        for (var row : db.queryForList("SELECT l.code,l.space_use,COUNT(e.id) AS n FROM campus_location l LEFT JOIN equipment e ON e.location_id=l.id GROUP BY l.id,l.code,l.space_use")) {
            int expected = switch(row.get("space_use").toString()) {
                case "DORM_ROOM" -> 8; case "CLASSROOM" -> 6; case "OFFICE" -> 4;
                case "CANTEEN_AREA","GYM_GALLERY" -> 2; case "GYM_COURT" -> 3; default -> 0;
            };
            assertEquals(expected, ((Number)row.get("n")).intValue(), row.get("code").toString());
        }
        assertEquals(10, db.queryForObject("SELECT COUNT(*) FROM campus_location l JOIN campus_floor f ON l.floor_id=f.id WHERE f.code='wb1-f1' AND l.space_use='DORM_ROOM'", Integer.class));
        assertEquals(12, db.queryForObject("SELECT COUNT(*) FROM campus_location l JOIN campus_floor f ON l.floor_id=f.id WHERE f.code='wb1-f2' AND l.space_use='DORM_ROOM'", Integer.class));
    }

    @Test void second_init_preserves_modified_names_flags_and_identity() {
        service.initialize(config);
        assertEquals(1817, count("equipment"));
        var firstId = db.queryForObject("SELECT id FROM equipment WHERE initialization_key='wb1-f1-r101:bed-left'", Long.class);
        db.update("UPDATE equipment SET name='维护后的床桌名称',enabled=0,version=version+1 WHERE id=?", firstId);
        db.update("UPDATE campus_location SET name='修正后的显示标签',version=version+1 WHERE code='wb1-f1-r101'");
        assertFalse(service.initialize(config));
        assertEquals(1817, count("equipment"));
        assertEquals(firstId, db.queryForObject("SELECT id FROM equipment WHERE initialization_key='wb1-f1-r101:bed-left'", Long.class));
        assertEquals("维护后的床桌名称", db.queryForObject("SELECT name FROM equipment WHERE id=?", String.class, firstId));
        assertFalse(db.queryForObject("SELECT enabled FROM equipment WHERE id=?", Boolean.class, firstId));
        assertEquals("修正后的显示标签", db.queryForObject("SELECT name FROM campus_location WHERE code='wb1-f1-r101'", String.class));
        assertEquals(1, count("campus_initialization"));
    }

    @Test void incompatible_configuration_does_not_overwrite_or_partially_import() {
        service.initialize(config);
        db.update("UPDATE campus_location SET type='AREA' WHERE code='wb1-f1-r101'");
        assertThrows(IllegalStateException.class, () -> service.initialize(config));
        assertEquals(1817, count("equipment"));
        assertEquals("AREA", db.queryForObject("SELECT type FROM campus_location WHERE code='wb1-f1-r101'", String.class));
    }
}
