package com.autosign;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan(basePackages = {"com.autosign.mapper", "com.autosign.activity"},
        annotationClass = org.apache.ibatis.annotations.Mapper.class)
@EnableScheduling
public class AutoSignApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoSignApplication.class, args);
    }
}
