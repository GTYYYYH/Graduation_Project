package cn.wzbc.maintenance.campus.mapper;

import cn.wzbc.maintenance.campus.entity.CampusLocationEntity;
import cn.wzbc.maintenance.campus.vo.LocationSummaryVo.StageCounts;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CampusBusinessMapper {
    class Scope {
        public final String role;
        public final long userId;
        public final boolean qualified;
        public final Long buildingId,floorId,locationId;
        public Scope(String role,long userId,boolean qualified,Long buildingId,Long floorId,Long locationId) { this.role=role; this.userId=userId; this.qualified=qualified; this.buildingId=buildingId; this.floorId=floorId; this.locationId=locationId; }
    }
    class CountRow {
        public Long locationId;
        public long pendingAccept,reassignPending,inProgress,rework,pendingAcceptance,needsSupplement,pendingReview;
        public StageCounts counts() { return new StageCounts(pendingAccept,reassignPending,inProgress,rework,pendingAcceptance,needsSupplement,pendingReview); }
    }
    class ItemRow {
        public Long id;
        public String resourceType,businessNo,equipmentName,status,stage;
        public Instant createdAt;
    }
    List<CountRow> aggregate(@Param("scope") Scope scope);
    long itemCount(@Param("scope") Scope scope,@Param("category") String category);
    List<ItemRow> items(@Param("scope") Scope scope,@Param("category") String category,@Param("limit") int limit,@Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM campus_building WHERE id=#{id}") int building(long id);
    @Select("SELECT building_id FROM campus_floor WHERE id=#{id}") Long floorBuilding(long id);
    @Select("SELECT * FROM campus_location WHERE id=#{id}") CampusLocationEntity location(long id);
}
