package com.dormitory;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.dormitory.mapper")
@EnableScheduling
public class SmartDormitoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartDormitoryApplication.class, args);
    }
}
