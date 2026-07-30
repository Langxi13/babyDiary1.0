package com.langxi.babydiary.identity.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class AccountMailService {
    private final JavaMailSender sender;
    private final boolean enabled;
    private final String from;
    private final String publicUrl;

    public AccountMailService(ObjectProvider<JavaMailSender> provider,
                              @Value("${app.mail.enabled:false}") boolean enabled,
                              @Value("${app.mail.from:}") String from,
                              @Value("${app.mail.public-url:http://localhost:5173}") String publicUrl) {
        this.sender = provider.getIfAvailable();
        this.enabled = enabled;
        this.from = from;
        this.publicUrl = publicUrl;
    }

    public boolean enabled() {
        return enabled && sender != null && from != null && !from.isBlank();
    }

    public void verification(String email, String token) {
        send(email, "验证 Baby Diary 邮箱",
                "请在 24 小时内打开以下链接完成验证：\n" + publicUrl + "/profile#verifyEmail=" + token);
    }

    public void passwordReset(String email, String token) {
        send(email, "重置 Baby Diary 密码",
                "请在 30 分钟内打开以下链接重置密码：\n" + publicUrl + "/login#resetToken=" + token);
    }

    private void send(String recipient, String subject, String body) {
        if (!enabled()) return;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}
