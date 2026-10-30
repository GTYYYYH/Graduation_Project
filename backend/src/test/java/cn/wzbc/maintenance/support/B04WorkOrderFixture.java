package cn.wzbc.maintenance.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Isolated SQL history material only: no future workflow endpoint or production seeding. */
public final class B04WorkOrderFixture {
    private final JdbcTemplate db; private final Instant now;
    public B04WorkOrderFixture(JdbcTemplate db,Instant now) { this.db=db; this.now=now; }
    public long equipment(long location) {
        long id=IdWorker.getId(); db.update("INSERT INTO equipment(id,created_at,equipment_no,name,location_id,address_text,facility_type,enabled,version,updated_at) VALUES(?,?,?,'演示空调',?,'台账地址','AIR_CONDITIONER',1,0,?)",id,now,"EQ-"+id,location,now); return id;
    }
    public long order(long request,long equipment,long technician,long admin,String status) {
        var r=db.queryForMap("SELECT * FROM repair_request WHERE id=?",request); long id=IdWorker.getId(); boolean accepted=!status.equals("PENDING_ACCEPT");
        db.update("INSERT INTO work_order(id,created_at,work_order_no,request_id,equipment_id,reporter_id,location_mode,location_id,location_name_snapshot,status,version,current_round,updated_at,assigned_technician_id,accepted_technician_id,accepted_at,completed_at) VALUES(?,?,?,?,?,?,?,?,?,?,0,?,?,?, ?,?,?)",id,now,"WO-"+id,request,equipment,r.get("reporter_id"),r.get("location_mode"),r.get("location_id"),r.get("location_name_snapshot"),status,accepted?1:0,now,technician,accepted?technician:null,accepted?now:null,status.equals("COMPLETED")?now.plusSeconds(10):null);
        long assignment=assignment(id,1,technician,admin,((Number)r.get("current_submission_id")).longValue(),accepted?"ACCEPTED":"PENDING");
        db.update("UPDATE work_order SET current_assignment_id=? WHERE id=?",assignment,id);
        db.update("UPDATE repair_request SET status='APPROVED',matched_equipment_id=?,version=version+1 WHERE id=?",equipment,request);
        if(accepted) round(id,technician,status.equals("COMPLETED")); return id;
    }
    public long assignment(long order,int number,long technician,long admin,long submission,String outcome) {
        long id=IdWorker.getId(); boolean ended=!outcome.equals("PENDING"); db.update("INSERT INTO work_order_assignment(id,created_at,work_order_id,assignment_no,technician_id,assigned_by,visible_submission_id,assigned_at,ended_at,outcome,reason,decision_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",id,now,order,number,technician,admin,submission,now,ended?now.plusSeconds(1):null,outcome,outcome.equals("REFUSED")||outcome.equals("REPLACED")?"当次拒单或改派原因":null,ended?now.plusSeconds(1):null); return id;
    }
    public long round(long order,long technician,boolean completed) {
        long id=IdWorker.getId(); Long reporter=db.queryForObject("SELECT reporter_id FROM work_order WHERE id=?",Long.class,order);
        db.update("INSERT INTO repair_round(id,created_at,work_order_id,round_no,technician_id,started_at,submitted_at,result_text,acceptance_passed,accepted_by,acceptance_at) VALUES(?,?,?,1,?,?,?,?,?,?,?)",id,now,order,technician,now,completed?now.plusSeconds(2):null,completed?"当轮维修结果":null,completed?true:null,completed?reporter:null,completed?now.plusSeconds(3):null); return id;
    }
    public long formerBeforeCurrent(long order,long former,long admin,long submission) {
        db.update("UPDATE work_order_assignment SET assignment_no=2,assigned_at=?,ended_at=NULL,decision_at=IF(outcome='ACCEPTED',?,NULL) WHERE work_order_id=? AND assignment_no=1",now.plusSeconds(2),now.plusSeconds(2),order);
        db.update("UPDATE work_order SET accepted_at=IF(accepted_at IS NULL,NULL,?) WHERE id=?",now.plusSeconds(2),order);
        db.update("UPDATE repair_round SET started_at=? WHERE work_order_id=?",now.plusSeconds(2),order);
        return assignment(order,1,former,admin,submission,"REFUSED");
    }
}
