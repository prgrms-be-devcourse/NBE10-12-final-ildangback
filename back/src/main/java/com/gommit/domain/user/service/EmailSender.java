package com.gommit.domain.user.service;

public interface EmailSender {

    void send(String to, String subject, String body);
}
