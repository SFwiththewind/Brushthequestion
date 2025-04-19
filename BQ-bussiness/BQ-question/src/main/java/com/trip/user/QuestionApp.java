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
@ComponentScan(basePackages = {"com.trip.user", "controller", "service", "mapper", "untils","config"})
@MapperScan("mapper")
public class QuestionApp

{
    public static void main( String[] args )
    {

        SpringApplication.run(QuestionApp.class, args);
    }
}
