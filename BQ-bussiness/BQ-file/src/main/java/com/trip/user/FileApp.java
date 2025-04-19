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
@ComponentScan(basePackages = {"com.trip.user", "controller", "mapper", "service", "service.Impl","config","untils"})
@MapperScan("mapper")
public class FileApp
{
    public static void main( String[] args )
    {
        SpringApplication.run(FileApp.class,args);

    }
}
