package com.gommit.domain.user.service;

public interface VerificationMailSender {

    void send(String email, String verificationLink);
}
