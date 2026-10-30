package cn.wzbc.maintenance.workorder.mapper;

import cn.wzbc.maintenance.workorder.entity.AssignmentEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AssignmentCandidateMapper {
    class Metrics {
        public long id,W,I,R,A,todayCompleted;
        public Instant lastAssignedAt;
        public long doubledLoad() { return 2*W+4*I+6*R+A; }
    }
    List<Metrics> metrics(@Param("start") Instant start,@Param("end") Instant end);
    @Select("SELECT * FROM work_order_assignment WHERE work_order_id=#{id} AND outcome='REFUSED' ORDER BY assigned_at DESC,id DESC")
    List<AssignmentEntity> refusals(long id);
}
