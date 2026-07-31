package com.langxi.babydiary.platform.infrastructure;

import com.langxi.babydiary.platform.api.ApiContract;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {
    private final SlowRequestLoggingInterceptor slowRequests;

    public WebMvcConfiguration(SlowRequestLoggingInterceptor slowRequests) {
        this.slowRequests = slowRequests;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(slowRequests).addPathPatterns(ApiContract.ROOT + "/**");
    }
}
