package com.langxi.babydiary.config;

import com.langxi.babydiary.service.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private LoginService loginService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .antMatchers("/login", "/register", "/webjars/**", "/css/**").permitAll() // 允许匿名访问登录、注册页面和静态资源
                .anyRequest().authenticated() // 其他页面需要登录
                .and()
                .formLogin()
                .loginPage("/login") // 自定义登录页面
                .loginProcessingUrl("/login") // 登录处理 URL
                .defaultSuccessUrl("/home") // 登录成功后跳转到首页
                .failureUrl("/login?error=true") // 登录失败后跳转到登录页面并显示错误信息
                .permitAll()
                .and()
                .logout()
                .logoutUrl("/logout") // 注销 URL
                .logoutSuccessUrl("/login?logout=true") // 注销成功后跳转到登录页面
                .invalidateHttpSession(true) // 使会话失效
                .deleteCookies("JSESSIONID") // 删除 JSESSIONID Cookie
                .permitAll();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(loginService).passwordEncoder(passwordEncoder);
    }
}