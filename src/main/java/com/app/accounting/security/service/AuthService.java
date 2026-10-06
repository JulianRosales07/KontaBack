package com.app.accounting.security.service;

import com.app.accounting.security.dto.request.ForgotPasswordRequest;
import com.app.accounting.security.dto.request.GoogleAuthRequest;
import com.app.accounting.security.dto.request.LoginRequest;
import com.app.accounting.security.dto.request.RegisterRequest;
import com.app.accounting.security.dto.request.ResetPasswordRequest;
import com.app.accounting.security.dto.response.AuthResponse;
import com.app.accounting.security.dto.response.MessageResponse;
import com.app.accounting.security.dto.response.UserResponse;

public interface AuthService {
    AuthResponse login(LoginRequest request);
    AuthResponse register(RegisterRequest request);
    AuthResponse loginWithGoogle(GoogleAuthRequest request);
    UserResponse getCurrentUser();
    MessageResponse forgotPassword(ForgotPasswordRequest request);
    MessageResponse resetPassword(ResetPasswordRequest request);
}
