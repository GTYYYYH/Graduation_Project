package cn.wzbc.maintenance.campus;

import cn.wzbc.maintenance.Application;
import cn.wzbc.maintenance.security.CurrentUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@Transactional
class CampusCatalogIT {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;

    private org.springframework.test.web.servlet.request.RequestPostProcessor identity(CurrentUser.Role role) {
        var review = role == CurrentUser.Role.TECHNICIAN ? CurrentUser.ReviewStatus.PENDING : null;
        var user = new CurrentUser(9007199254740993L, role, true, review, 0);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test void empty_catalog_is_real_not_hardcoded() throws Exception {
        mvc.perform(get("/api/campus/catalog").with(identity(CurrentUser.Role.REPORTER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.buildings").isEmpty())
                .andExpect(jsonPath("$.data.floors").isEmpty()).andExpect(jsonPath("$.data.locations").isEmpty());
    }

    @Test void explicit_floor_identity_and_observation_are_separate_from_reporting() throws Exception {
        assertEquals(8, db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('campus_building','campus_floor','campus_location','equipment','equipment_event','equipment_location_history','location_name_history','campus_initialization')", Integer.class));
        db.update("INSERT INTO campus_building VALUES (9007199254741001,UTC_TIMESTAMP(3),'fixture-a','测试楼A','DORMITORY',0,'fixture'),(9007199254741002,UTC_TIMESTAMP(3),'fixture-b','测试楼B','DORMITORY',1,'fixture')");
        db.update("INSERT INTO campus_floor VALUES (9007199254741011,UTC_TIMESTAMP(3),9007199254741001,1,'fixture-a-f1','首层',0,'fixture'),(9007199254741012,UTC_TIMESTAMP(3),9007199254741002,1,'fixture-b-f1','首层',0,'fixture')");
        db.update("INSERT INTO campus_location VALUES (9007199254741021,UTC_TIMESTAMP(3),9007199254741011,'fixture-a-101','ROOM','DORM_ROOM','101',true,0,'fixture',0,UTC_TIMESTAMP(3)),(9007199254741022,UTC_TIMESTAMP(3),9007199254741012,'fixture-b-101','ROOM','DORM_ROOM','101',true,0,'fixture',0,UTC_TIMESTAMP(3)),(9007199254741023,UTC_TIMESTAMP(3),9007199254741012,'fixture-b-lobby','AREA','DORM_LOBBY','门厅',false,1,'fixture',0,UTC_TIMESTAMP(3))");
        mvc.perform(get("/api/campus/catalog").with(identity(CurrentUser.Role.REPORTER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.locations[0].id").value("9007199254741021"))
                .andExpect(jsonPath("$.data.locations[1].id").value("9007199254741022"))
                .andExpect(jsonPath("$.data.locations[1].floorId").value("9007199254741012"))
                .andExpect(jsonPath("$.data.locations[0].canCreateRequest").value(true))
                .andExpect(jsonPath("$.data.locations[2].reportable").value(false))
                .andExpect(jsonPath("$.data.locations[2].canCreateRequest").value(false));
        mvc.perform(get("/api/campus/catalog").with(identity(CurrentUser.Role.TECHNICIAN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.locations.length()").value(3))
                .andExpect(jsonPath("$.data.locations[0].canCreateRequest").value(false));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> db.update("INSERT INTO campus_floor VALUES (9007199254741099,UTC_TIMESTAMP(3),9007199254741001,1,'fixture-duplicate','错误重复层',0,'fixture')"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> db.update("INSERT INTO campus_location VALUES (9007199254741098,UTC_TIMESTAMP(3),999,'fixture-orphan','ROOM','DORM_ROOM','101',true,0,'fixture',0,UTC_TIMESTAMP(3))"));
    }

    @Test void anonymous_cannot_read_catalog() throws Exception {
        mvc.perform(get("/api/campus/catalog")).andExpect(status().isUnauthorized());
    }
}
