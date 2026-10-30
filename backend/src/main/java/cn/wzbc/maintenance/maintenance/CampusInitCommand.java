package cn.wzbc.maintenance.maintenance;

import cn.wzbc.maintenance.campus.service.CampusInitializationService;
import java.nio.file.Path;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class CampusInitCommand implements LocalMaintenanceCommand {
    private final CampusInitializationService service;
    private final Environment environment;
    public CampusInitCommand(CampusInitializationService service,Environment environment) { this.service=service; this.environment=environment; }
    @Override public String name() { return "campus-init"; }
    @Override public MaintenanceResult execute() {
        var root=Path.of(environment.getRequiredProperty("campus.project-root"));
        boolean changed=service.initialize(root.resolve("config/campus.json"));
        return new MaintenanceResult(name(),changed,null,"campus-v1",new Counts(10,41,296,257,1817), changed ? "示意校园初始数据已导入" : "已初始化，兼容核对通过，保留现有资料");
    }
}
