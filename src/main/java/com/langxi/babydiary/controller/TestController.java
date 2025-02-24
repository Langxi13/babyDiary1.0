package com.langxi.babydiary.controller;

import com.langxi.babydiary.mapper.DiaryMapper;
import com.langxi.babydiary.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TestController {
    @Autowired
    private DiaryMapper diaryMapper;

    @Autowired
    private UserMapper userMapper;

    @GetMapping(value = "/test")
    public String test() {
        System.out.println(diaryMapper.findAllDiaries());
        System.out.println(userMapper.findByUsername("langxi"));
        return "home";
    }
}
