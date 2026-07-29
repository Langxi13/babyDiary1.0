package com.langxi.babydiary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.langxi.babydiary", excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.langxi\\.babydiary\\.v3(\\..*)?"))
@MapperScan("com.langxi.babydiary.mapper")
@EnableScheduling
@EnableAsync
public class BabyDiaryApplication {

    public static void main(String[] args) {
        SpringApplication.run(BabyDiaryApplication.class, args);
    }

}
