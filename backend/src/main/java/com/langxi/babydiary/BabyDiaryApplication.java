package com.langxi.babydiary;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan({
    "com.langxi.babydiary.identity.infrastructure",
    "com.langxi.babydiary.space.infrastructure",
    "com.langxi.babydiary.diary.infrastructure",
    "com.langxi.babydiary.media.infrastructure",
    "com.langxi.babydiary.album.infrastructure",
    "com.langxi.babydiary.tag.infrastructure",
    "com.langxi.babydiary.draft.infrastructure",
    "com.langxi.babydiary.anniversary.infrastructure",
    "com.langxi.babydiary.ai.infrastructure",
    "com.langxi.babydiary.notification.infrastructure",
    "com.langxi.babydiary.reminder.infrastructure",
    "com.langxi.babydiary.sync.infrastructure",
    "com.langxi.babydiary.share.infrastructure",
    "com.langxi.babydiary.transfer.infrastructure",
    "com.langxi.babydiary.home.infrastructure",
    "com.langxi.babydiary.platform.infrastructure"
})
@EnableScheduling
public class BabyDiaryApplication {
    public static void main(String[] args) {
        SpringApplication.run(BabyDiaryApplication.class, args);
    }
}
