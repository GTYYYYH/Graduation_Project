package cn.wzbc.maintenance.repairrequest.mapper;
import cn.wzbc.maintenance.repairrequest.entity.*;
import cn.wzbc.maintenance.campus.entity.CampusLocationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.*;
@Mapper
public interface RepairRequestMapper extends BaseMapper<RepairRequestEntity> {
    @Select("SELECT * FROM repair_request WHERE id=#{id} FOR UPDATE") RepairRequestEntity lock(long id);
    @Select("SELECT * FROM repair_request_submission WHERE id=#{id}") RequestSubmissionEntity submission(long id);
    @Select("SELECT * FROM repair_request_submission WHERE request_id=#{id} ORDER BY submission_no DESC,id DESC") List<RequestSubmissionEntity> submissions(long id);
    @Select("SELECT * FROM repair_request_event WHERE request_id=#{id} ORDER BY occurred_at DESC,id DESC") List<RequestEventEntity> events(long id);
    @Select("SELECT * FROM campus_location WHERE id=#{id}") CampusLocationEntity location(long id);
    @Select("SELECT CONCAT(b.name,' · ',f.name,' · ',l.name) FROM campus_location l JOIN campus_floor f ON f.id=l.floor_id JOIN campus_building b ON b.id=f.building_id WHERE l.id=#{id}") String locationName(long id);
    @Insert("INSERT INTO repair_request_submission(id,created_at,request_id,submission_no,actor_user_id,equipment_name,equipment_category,address_text,description,contact_name,contact_phone,location_mode,location_id,location_name_snapshot,submitted_at,operation_type,operation_id,content_digest,result_version) VALUES(#{id},#{createdAt},#{requestId},#{submissionNo},#{actorUserId},#{equipmentName},#{equipmentCategory},#{addressText},#{description},#{contactName},#{contactPhone},#{locationMode},#{locationId},#{locationNameSnapshot},#{submittedAt},#{operationType},#{operationId},#{contentDigest},#{resultVersion})")
    int insertSubmission(RequestSubmissionEntity submission);
    @Insert("INSERT INTO repair_request_event(id,created_at,request_id,actor_user_id,submission_id,event_type,from_status,to_status,reason,occurred_at,evidence_equipment_id,operation_type,operation_id,content_digest,result_resource_type,result_resource_id,result_version,result_round) VALUES(#{id},#{createdAt},#{requestId},#{actorUserId},#{submissionId},#{eventType},#{fromStatus},#{toStatus},#{reason},#{occurredAt},#{evidenceEquipmentId},#{operationType},#{operationId},#{contentDigest},#{resultResourceType},#{resultResourceId},#{resultVersion},#{resultRound})")
    int insertEvent(RequestEventEntity event);
}
