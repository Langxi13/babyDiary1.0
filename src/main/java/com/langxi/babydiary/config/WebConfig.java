package com.langxi.babydiary.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${diaryFilePath}") // 从配置文件中读取本地文件存储路径
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将本地文件系统的目录映射为 HTTP 路径
        registry.addResourceHandler("/images/**") // HTTP 访问路径
                .addResourceLocations("file:" + uploadDir); // 本地文件系统路径
    }
}