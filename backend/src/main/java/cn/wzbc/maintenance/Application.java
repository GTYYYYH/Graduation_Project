package cn.wzbc.maintenance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class Application {
    public static void main(String[] args) {
        var context=SpringApplication.run(Application.class,args);
        // Explicit maintenance is a one-shot process; close its scheduler/pool after delivery.
        if(context.getEnvironment().getProperty("maintenance.command")!=null)context.close();
    }
}
