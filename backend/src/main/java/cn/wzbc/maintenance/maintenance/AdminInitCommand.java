package cn.wzbc.maintenance.maintenance;

import cn.wzbc.maintenance.account.service.AdminInitializationService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AdminInitCommand implements LocalMaintenanceCommand {
    private final AdminInitializationService service;
    private final Environment environment;
    public AdminInitCommand(AdminInitializationService service, Environment environment) { this.service=service; this.environment=environment; }
    @Override public String name() { return "admin-init"; }
    @Override public MaintenanceResult execute() {
        var result=service.initialize(environment.getProperty("maintenance.admin.login-name"),environment.getProperty("maintenance.admin.name"),environment.getProperty("maintenance.admin.phone"));
        if (result.changed()) {
            // 仅显式本地命令事务成功后的一次性控制台交付，不使用日志框架或返回API。
            System.out.println("ADMIN_INITIAL_PASSWORD="+result.newPassword());
        }
        return new MaintenanceResult(name(),result.changed(),result.userId().toString(),null,null,
                result.changed() ? "预置管理员已创建，请妥善保存本次密码" : "已初始化，保留原账号和密码；遗忘时使用后续专用本地重置流程");
    }
}
