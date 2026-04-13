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

    public void sendVerificationCode(String toEmail, String code) {
        sendEmail(
                toEmail,
                "CloudTeachingAI - 邮箱验证码",
                buildVerificationEmailContent(code)
        );
        log.info("Verification code email sent successfully: {}", toEmail);
    }

    public void sendTeacherApprovalEmail(String toEmail, String username) {
        sendEmail(
                toEmail,
                "CloudTeachingAI - 教师注册申请已通过",
                buildTeacherApprovalEmailContent(username)
        );
        log.info("Teacher approval email sent successfully: {}", toEmail);
    }

    private void sendEmail(String toEmail, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(content);

        try {
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to {}, error: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("邮件发送失败，请稍后重试");
        }
    }

    private String buildVerificationEmailContent(String code) {
        return """
                您好，

                您正在注册 CloudTeachingAI 智能云端教学平台账号。
                您的验证码是：%s

                验证码有效期为 15 分钟，请尽快完成验证。
                如果这不是您的操作，请忽略此邮件。

                -------------------
                CloudTeachingAI
                智能云端教学平台
                """.formatted(code);
    }

    private String buildTeacherApprovalEmailContent(String username) {
        return """
                %s，您好：

                您提交的教师注册申请已通过管理员审核。
                现在您已经可以使用注册时填写的邮箱和密码登录 CloudTeachingAI 平台。

                如这不是您的操作，请及时联系平台管理员。

                -------------------
                CloudTeachingAI
                智能云端教学平台
                """.formatted(username);
    }
}
