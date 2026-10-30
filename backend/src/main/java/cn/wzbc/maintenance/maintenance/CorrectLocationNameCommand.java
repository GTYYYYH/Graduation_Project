package cn.wzbc.maintenance.maintenance;
import cn.wzbc.maintenance.campus.service.LocationNameMaintenanceService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
@Component
public class CorrectLocationNameCommand implements LocalMaintenanceCommand {
    private final LocationNameMaintenanceService service;private final Environment environment;
    public CorrectLocationNameCommand(LocationNameMaintenanceService service,Environment environment){this.service=service;this.environment=environment;}
    public String name(){return "correct-location-name";}
    public MaintenanceResult execute(){var r=service.correct(environment.getRequiredProperty("maintenance.location.id",Long.class),environment.getRequiredProperty("maintenance.location.version",Integer.class),environment.getRequiredProperty("maintenance.location.name"),environment.getRequiredProperty("maintenance.operator-label"),environment.getRequiredProperty("maintenance.reason"));return new MaintenanceResult(name(),r.changed(),Long.toString(r.locationId()),null,null,"同稳定ID名称已核对；保留历史快照及空间归属，当前版本="+r.version());}
}
