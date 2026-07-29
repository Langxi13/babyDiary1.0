package com.langxi.babydiary.v3;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class, scanBasePackages = {
        "com.langxi.babydiary.v3",
        "com.langxi.babydiary.storage"
})
@MapperScan({
        "com.langxi.babydiary.v3.identity.infrastructure",
        "com.langxi.babydiary.v3.space.infrastructure",
        "com.langxi.babydiary.v3.diary.infrastructure",
        "com.langxi.babydiary.v3.media.infrastructure",
        "com.langxi.babydiary.v3.album.infrastructure",
        "com.langxi.babydiary.v3.tag.infrastructure",
        "com.langxi.babydiary.v3.draft.infrastructure",
        "com.langxi.babydiary.v3.anniversary.infrastructure",
        "com.langxi.babydiary.v3.ai.infrastructure",
        "com.langxi.babydiary.v3.notification.infrastructure",
        "com.langxi.babydiary.v3.reminder.infrastructure",
        "com.langxi.babydiary.v3.sync.infrastructure",
        "com.langxi.babydiary.v3.share.infrastructure",
        "com.langxi.babydiary.v3.transfer.infrastructure",
        "com.langxi.babydiary.v3.platform.infrastructure"
})
@EnableScheduling
public class BabyDiaryV3Application {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(BabyDiaryV3Application.class);
        application.setAdditionalProfiles("v3");
        application.run(args);
    }
}
