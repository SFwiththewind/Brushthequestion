package com.trip.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Hello world!
 *
 */

@SpringBootApplication
@MapperScan("com.trip.user.mapper")
@ComponentScan(basePackages = {"com.trip.user", "untils","com.trip.user.service"})

public class BQUserApp {
    public static void main(String[] args) {
        SpringApplication.run(BQUserApp.class, args);
    }
}
