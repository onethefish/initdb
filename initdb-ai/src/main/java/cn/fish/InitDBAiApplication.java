package cn.fish;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication()
public class InitDBAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(InitDBAiApplication.class, args);
    }

}
