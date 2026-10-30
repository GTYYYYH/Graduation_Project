package cn.wzbc.maintenance.equipment.mapper;

import cn.wzbc.maintenance.equipment.entity.EquipmentEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.time.Instant;

@Mapper
public interface EquipmentMapper extends BaseMapper<EquipmentEntity> {
    @Select("SELECT * FROM equipment WHERE id=#{id} FOR UPDATE") EquipmentEntity lock(long id);
    @Select("SELECT COUNT(*) FROM work_order WHERE equipment_id=#{id} AND status<>'COMPLETED' FOR UPDATE") long unfinished(long id);
    @Insert("INSERT INTO equipment_event(id,created_at,equipment_id,actor_user_id,event_type,occurred_at,operation_type,operation_id,content_digest,result_version) VALUES(#{event},#{now},#{equipment},#{actor},#{type},#{now},#{operationType},#{operation},#{digest},#{version})")
    int event(@Param("event") long event,@Param("equipment") long equipment,@Param("actor") long actor,@Param("type") String type,@Param("operationType") String operationType,@Param("operation") String operation,@Param("digest") byte[] digest,@Param("version") int version,@Param("now") Instant now);
    @Insert("INSERT INTO equipment_location_history(id,created_at,equipment_id,actor_user_id,old_location_id,new_location_id,old_address_text,new_address_text,reason,changed_at,equipment_event_id) VALUES(#{id},#{now},#{equipment},#{actor},#{oldLocation},#{newLocation},#{oldAddress},#{newAddress},#{reason},#{now},#{event})")
    int locationHistory(@Param("id") long id,@Param("equipment") long equipment,@Param("actor") long actor,@Param("oldLocation") Long oldLocation,@Param("newLocation") long newLocation,@Param("oldAddress") String oldAddress,@Param("newAddress") String newAddress,@Param("reason") String reason,@Param("event") long event,@Param("now") Instant now);
}
