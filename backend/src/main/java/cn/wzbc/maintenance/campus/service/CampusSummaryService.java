package cn.wzbc.maintenance.campus.service;

import cn.wzbc.maintenance.campus.mapper.CampusBusinessMapper;
import cn.wzbc.maintenance.campus.mapper.CampusBusinessMapper.*;
import cn.wzbc.maintenance.campus.mapper.CampusMapper;
import cn.wzbc.maintenance.campus.vo.*;
import cn.wzbc.maintenance.campus.vo.LocationSummaryVo.*;
import cn.wzbc.maintenance.campus.vo.LocationItemVo.LocationItemsVo;
import cn.wzbc.maintenance.common.PageResult;
import cn.wzbc.maintenance.security.*;
import cn.wzbc.maintenance.account.vo.CurrentUserVo;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static cn.wzbc.maintenance.common.OperationResultService.*;

@Service
public class CampusSummaryService {
    private final CampusBusinessMapper mapper;
    private final CampusMapper catalog;
    private final BusinessAccessPolicy access;
    private final Clock clock;
    public CampusSummaryService(CampusBusinessMapper mapper,CampusMapper catalog,BusinessAccessPolicy access,Clock clock) { this.mapper=mapper; this.catalog=catalog; this.access=access; this.clock=clock; }
    private CurrentUser actual(CurrentUser actor) {
        var user=access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN,CurrentUser.Role.REPORTER,CurrentUser.Role.TECHNICIAN),false);
        return new CurrentUser(user.id,user.role,user.enabled,user.reviewStatus,user.version);
    }
    private Scope scope(CurrentUser actor,Long building,Long floor,Long location) { return new Scope(actor.role().name(),actor.id(),actor.reviewStatus()==CurrentUser.ReviewStatus.APPROVED,building,floor,location); }
    private Long optionalId(String value) { return value==null?null:id(value); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public CampusSummariesVo summaries(CurrentUser actor,String buildingId,String floorId) {
        var user=actual(actor); Long building=optionalId(buildingId), floor=optionalId(floorId);
        if(building!=null && mapper.building(building)!=1) throw error(400,"INVALID_QUERY","楼栋不存在");
        if(floor!=null) { Long parent=mapper.floorBuilding(floor); if(parent==null || (building!=null && !building.equals(parent))) throw error(400,"INVALID_QUERY","楼层与楼栋不匹配"); }
        String at=CurrentUserVo.instant(clock.instant());
        var rows=mapper.aggregate(scope(user,building,floor,null)).stream().map(row -> { var counts=row.counts(); return new LocationSummaryVo(row.locationId.toString(),counts.category(),counts,counts.total(),at); }).toList();
        String version=catalog.configVersion(); return new CampusSummariesVo(version==null?"":version,rows,at);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public LocationItemsVo items(CurrentUser actor,long locationId,int page,int pageSize,String category) {
        var user=actual(actor); page(page,pageSize,null);
        if(category!=null && !Set.of("FAULT_PROCESSING","PENDING_ACCEPTANCE","NEEDS_SUPPLEMENT","PENDING_REVIEW").contains(category)) throw error(400,"INVALID_QUERY","事项类别不合法");
        var location=mapper.location(locationId); if(location==null) throw error(404,"RESOURCE_NOT_FOUND","位置不存在");
        var scope=scope(user,null,null,locationId); String at=CurrentUserVo.instant(clock.instant());
        var counts=mapper.aggregate(scope).stream().findFirst().map(CountRow::counts).orElseGet(() -> new StageCounts(0,0,0,0,0,0,0));
        long total=mapper.itemCount(scope,category);
        var rows=mapper.items(scope,category,pageSize,(long)(page-1)*pageSize).stream().map(row -> new LocationItemVo(row.resourceType,row.id.toString(),row.businessNo,row.equipmentName,row.status,row.stage,CurrentUserVo.instant(row.createdAt),actions(row,user))).toList();
        var l=new CatalogVo.LocationVo(location.id.toString(),location.code,location.floorId.toString(),location.type,location.spaceUse,location.name,location.reportable,location.reportable && user.role()==CurrentUser.Role.REPORTER,location.displayOrder);
        return new LocationItemsVo(l,counts,counts.category(),counts.total(),new PageResult<>(rows,total,page,pageSize),at);
    }
    private List<String> actions(ItemRow row,CurrentUser user) {
        // Rows already enforce current ownership; this projection matches the existing detail actions.
        if(row.resourceType.equals("REPAIR_REQUEST")) {
            if(user.role()==CurrentUser.Role.ADMIN && row.status.equals("PENDING_REVIEW")) return List.of("RETURN_REQUEST","CLOSE_DUPLICATE","APPROVE_AND_ASSIGN");
            if(user.role()==CurrentUser.Role.REPORTER) return row.status.equals("NEEDS_SUPPLEMENT")?List.of("SUPPLEMENT_REQUEST","WITHDRAW_REQUEST"):List.of("WITHDRAW_REQUEST");
        } else {
            if(user.role()==CurrentUser.Role.ADMIN && Set.of("PENDING_ACCEPT","REASSIGN_PENDING").contains(row.status)) return List.of("ASSIGN");
            if(user.role()==CurrentUser.Role.REPORTER && row.status.equals("PENDING_ACCEPTANCE")) return List.of("ACCEPTANCE");
            if(user.role()==CurrentUser.Role.TECHNICIAN) {
                if(row.status.equals("PENDING_ACCEPT")) return List.of("ACCEPT","REFUSE");
                if(row.status.equals("IN_PROGRESS")) return List.of("PROGRESS","SUBMIT_RESULT");
            }
        }
        return List.of();
    }
}
