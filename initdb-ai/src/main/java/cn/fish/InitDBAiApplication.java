package cn.fish;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("cn.fish.initDB.mapper")
public class InitDBAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(InitDBAiApplication.class, args);
    }

}
