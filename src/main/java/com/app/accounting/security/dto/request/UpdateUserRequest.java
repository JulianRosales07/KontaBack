package com.app.accounting.security.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato de correo electrónico es inválido")
    private String email;

    private String fullName;

    private String phone;

    /** null = no modificar roles. Lista (posiblemente vacía) = reemplazar roles. */
    private List<String> roles;

    private Boolean enabled;
}
