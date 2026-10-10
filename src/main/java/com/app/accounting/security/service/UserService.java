package com.app.accounting.security.service;

import com.app.accounting.security.dto.request.ChangePasswordRequest;
import com.app.accounting.security.dto.request.CreateUserRequest;
import com.app.accounting.security.dto.request.UpdateUserRequest;
import com.app.accounting.security.dto.response.UserResponse;

import java.util.List;
import java.util.UUID;

public interface UserService {

    List<UserResponse> getAllUsers();

    UserResponse getUserById(UUID id);

    UserResponse createUser(CreateUserRequest request);

    UserResponse updateUser(UUID id, UpdateUserRequest request);

    UserResponse toggleUserStatus(UUID id, boolean enabled);

    UserResponse assignRoles(UUID id, List<String> roleCodes);

    UserResponse approveUser(UUID id, List<String> roleCodes);

    void changePassword(UUID id, ChangePasswordRequest request);
}
