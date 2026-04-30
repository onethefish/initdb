package cn.fish.web.config;


import cn.fish.web.aop.ControllerMethodLogAop;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author onethefish
 */
@Configuration
public class LogAutoConfiguration {

    @Bean
    public ControllerMethodLogAop controllerMethodLogAop() {
        return new ControllerMethodLogAop();
    }

}
