package cn.wzbc.maintenance.maintenance;
import cn.wzbc.maintenance.account.service.AdminPasswordMaintenanceService;
import org.springframework.stereotype.Component;
@Component
public class AdminResetCommand implements LocalMaintenanceCommand {
    private final AdminPasswordMaintenanceService service;
    public AdminResetCommand(AdminPasswordMaintenanceService service){this.service=service;}
    public String name(){return "admin-reset";}
    public MaintenanceResult execute(){var result=service.reset();System.out.println("ADMIN_RESET_PASSWORD="+result.newPassword());return new MaintenanceResult(name(),true,Long.toString(result.userId()),null,null,"原管理员密码已重置；请妥善接收本次一次性密码，全部旧登录已撤销");}
}
