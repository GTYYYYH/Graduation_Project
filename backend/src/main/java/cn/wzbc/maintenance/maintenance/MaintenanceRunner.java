package cn.wzbc.maintenance.maintenance;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@Component
public class MaintenanceRunner implements ApplicationRunner {
    private final Environment environment;
    private final ApplicationContext context;
    private final DataSource datasource;
    private final List<LocalMaintenanceCommand> commands;
    private final ObjectMapper mapper;
    public MaintenanceRunner(Environment environment, ApplicationContext context, DataSource datasource, List<LocalMaintenanceCommand> commands, ObjectMapper mapper) {
        this.environment=environment; this.context=context; this.datasource=datasource; this.commands=commands; this.mapper=mapper;
    }
    @Override public void run(ApplicationArguments arguments) throws Exception {
        String name=environment.getProperty("maintenance.command");
        if (name==null) return;
        if (context instanceof WebApplicationContext || environment.getActiveProfiles().length!=1) throw new IllegalStateException("维护命令必须禁用Web并显式选择一个环境");
        String target=environment.getRequiredProperty("maintenance.target-database");
        String expected="campus_maintenance_"+environment.getActiveProfiles()[0];
        try (var connection=datasource.getConnection()) {
            if (!target.equals(expected) || !target.equals(connection.getCatalog())) throw new IllegalStateException("维护目标库不匹配，未执行");
        }
        var command=commands.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow(() -> new IllegalStateException("未知本地维护命令"));
        System.out.println(mapper.writeValueAsString(command.execute()));
    }
}
