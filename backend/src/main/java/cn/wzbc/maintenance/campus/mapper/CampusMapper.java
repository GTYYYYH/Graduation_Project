package cn.wzbc.maintenance.campus.mapper;

import cn.wzbc.maintenance.campus.entity.*;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CampusMapper {
    @Select("SELECT * FROM campus_building ORDER BY display_order,id") List<CampusBuildingEntity> buildings();
    @Select("SELECT f.* FROM campus_floor f JOIN campus_building b ON f.building_id=b.id ORDER BY b.display_order,f.display_order,f.id") List<CampusFloorEntity> floors();
    @Select("SELECT l.* FROM campus_location l JOIN campus_floor f ON l.floor_id=f.id JOIN campus_building b ON f.building_id=b.id ORDER BY b.display_order,f.display_order,l.display_order,l.id") List<CampusLocationEntity> locations();
    @Select("SELECT config_version FROM campus_initialization ORDER BY initialized_at DESC,id DESC LIMIT 1") String configVersion();
    @Insert("INSERT INTO campus_building (id,created_at,code,name,type,display_order,config_version) VALUES (#{id},#{createdAt},#{code},#{name},#{type},#{displayOrder},#{configVersion})") int insertBuilding(CampusBuildingEntity entity);
    @Insert("INSERT INTO campus_floor (id,created_at,building_id,level,code,name,display_order,config_version) VALUES (#{id},#{createdAt},#{buildingId},#{level},#{code},#{name},#{displayOrder},#{configVersion})") int insertFloor(CampusFloorEntity entity);
    @Insert("INSERT INTO campus_location (id,created_at,floor_id,code,type,space_use,name,reportable,display_order,config_version,version,updated_at) VALUES (#{id},#{createdAt},#{floorId},#{code},#{type},#{spaceUse},#{name},#{reportable},#{displayOrder},#{configVersion},#{version},#{updatedAt})") int insertLocation(CampusLocationEntity entity);
    class InitializationSnapshot { public byte[] configDigest; }
    @Select("SELECT config_digest FROM campus_initialization WHERE config_version=#{version}") InitializationSnapshot initialization(String version);
    @Insert("INSERT INTO campus_initialization (id,created_at,config_version,config_digest,building_count,floor_count,location_count,reportable_count,equipment_count,initialized_at) VALUES (#{id},#{now},#{version},#{digest},10,41,296,257,1817,#{now})")
    int recordInitialization(@Param("id") long id, @Param("now") Instant now, @Param("version") String version, @Param("digest") byte[] digest);
}
