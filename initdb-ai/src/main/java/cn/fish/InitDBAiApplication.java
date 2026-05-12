package cn.fish;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan(basePackages = {"cn.fish.*.mapper"})
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class InitDBAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(InitDBAiApplication.class, args);
    }

}
