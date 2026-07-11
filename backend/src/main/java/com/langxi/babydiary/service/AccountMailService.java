package com.langxi.babydiary.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class AccountMailService {
    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.mail.from:}")
    private String from;

    @Value("${app.mail.public-url:http://localhost:5173}")
    private String publicUrl;

    public AccountMailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    public boolean isEnabled() {
        return enabled && mailSender != null && from != null && !from.isBlank();
    }

    public void sendVerification(String email, String token) {
        send(email, "验证 Baby Diary 邮箱",
                "请在 24 小时内打开以下链接完成验证：\n" + publicUrl + "/profile#verifyEmail=" + token);
    }

    public void sendPasswordReset(String email, String token) {
        send(email, "重置 Baby Diary 密码",
                "请在 30 分钟内打开以下链接重置密码：\n" + publicUrl + "/login#resetToken=" + token);
    }

    private void send(String recipient, String subject, String body) {
        if (!isEnabled()) return;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
