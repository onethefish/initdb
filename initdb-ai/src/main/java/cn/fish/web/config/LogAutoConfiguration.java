package cn.fish.web.config;


import cn.fish.cloud.serva.web.aop.ControllerMethodLogAop;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author onethefish
 */
@Configuration
public class LogAutoConfiguration {

    /**
     * 注入 serva 默认的日志输出
     */
    @Bean
    public ControllerMethodLogAop controllerMethodLogAop() {
        return new ControllerMethodLogAop();
    }

}
