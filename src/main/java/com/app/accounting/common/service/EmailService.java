package com.app.accounting.common.service;

public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetToken);
}
