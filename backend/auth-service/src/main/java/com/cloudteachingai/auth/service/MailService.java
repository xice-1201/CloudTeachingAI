package com.cloudteachingai.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * 发送验证码邮件
     */
    public void sendVerificationCode(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("CloudTeachingAI - 邮箱验证码");
        message.setContent(buildVerificationEmailContent(code));

        try {
            mailSender.send(message);
            log.info("验证码邮件发送成功: {}", toEmail);
        } catch (Exception e) {
            log.error("验证码邮件发送失败: {}, error: {}", toEmail, e.getMessage());
            throw new RuntimeException("邮件发送失败，请稍后重试");
        }
    }

    private String buildVerificationEmailContent(String code) {
        return """
                您好！

                您正在注册 CloudTeachingAI 智能云端教学平台账号。

                您的验证码是：%s

                验证码有效期为 15 分钟，请尽快完成验证。

                如果这不是您的操作，请忽略此邮件。

                -------------------
                CloudTeachingAI
                智能云端教学平台
                """.formatted(code);
    }
}
