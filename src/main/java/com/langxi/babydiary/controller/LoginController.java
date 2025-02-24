package com.langxi.babydiary.controller;

import com.langxi.babydiary.service.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    @Autowired
    private LoginService loginService;

    @GetMapping("/login")
    public String login() {
        return "login"; // 返回登录页面
    }
    @GetMapping("/home")
    public String home() {
        return "home"; // 返回登录页面
    }

    @GetMapping("/register")
    public String showRegisterPage() {
        return "register"; // 返回注册页面
    }

    @PostMapping("/register")
    public String registerUser(@RequestParam String username,
                               @RequestParam String password,
                               @RequestParam String confirmPassword,
                               @RequestParam String invitationCode,
                               Model model) {
        // 检查密码是否匹配
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            return "register";
        }

        // 注册用户
        try {
            loginService.registerUser(username, password, invitationCode);
            return "redirect:/login?registerSuccess=true";
        } catch (Exception e) {
            model.addAttribute("error", "Registration failed: " + e.getMessage());
            return "register";
        }
    }
}