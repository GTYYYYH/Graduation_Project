package cn.wzbc.maintenance.campus.service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import static cn.wzbc.maintenance.common.OperationResultService.*;
@Service
public class LocationNameMaintenanceService {
    private final JdbcTemplate db;private final Clock clock;
    public LocationNameMaintenanceService(JdbcTemplate db,Clock clock){this.db=db;this.clock=clock;}
    public record Result(long locationId,int version,boolean changed) {}
    @Transactional public Result correct(long location,int expectedVersion,String name,String operator,String reason){
        if(location<=0)throw error(400,"VALIDATION_FAILED","位置ID不合法");name=text(name==null?null:name.trim(),100,"新名称");operator=text(operator==null?null:operator.trim(),100,"本地维护者标识");reason=text(reason==null?null:reason.trim(),2000,"修正原因");
        var rows=db.queryForList("SELECT name,version FROM campus_location WHERE id=? FOR UPDATE",location);if(rows.isEmpty())throw error(404,"RESOURCE_NOT_FOUND","位置不存在");var current=rows.getFirst();int actual=((Number)current.get("version")).intValue();version(actual,expectedVersion);String old=current.get("name").toString();if(old.equals(name))return new Result(location,actual,false);
        var now=clock.instant().truncatedTo(ChronoUnit.MILLIS);if(db.update("UPDATE campus_location SET name=?,version=version+1,updated_at=? WHERE id=? AND version=?",name,now,location,actual)!=1)throw error(409,"VERSION_CONFLICT","位置资料已变化");
        db.update("INSERT INTO location_name_history(id,created_at,location_id,operator_label,old_name,new_name,reason,changed_at) VALUES(?,?,?,?,?,?,?,?)",IdWorker.getId(),now,location,operator,old,name,reason,now);return new Result(location,actual+1,true);
    }
}
