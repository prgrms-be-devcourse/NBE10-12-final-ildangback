package com.gommit.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class SmtpMailSender implements VerificationMailSender {

    private static final String SUBJECT = "[꼬밋] 이메일 인증을 완료해 주세요";
    private static final String BODY_FORMAT = "아래 링크를 눌러 이메일 인증을 완료해 주세요.%n%n%s";

    private final JavaMailSender javaMailSender;

    // Gmail 은 인증한 계정으로만 보낼 수 있어 MAIL_USERNAME 과 같아야 한다. SES, SendGrid 는 갈린다
    @Value("${app.mail-from}")
    private String from;

    @Override
    public void send(String email, String verificationLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(SUBJECT);
        message.setText(BODY_FORMAT.formatted(verificationLink));
        javaMailSender.send(message);
    }
}
