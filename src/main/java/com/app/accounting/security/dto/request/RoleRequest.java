package com.app.accounting.security.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleRequest {
    @NotBlank(message = "El código del rol es obligatorio")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,49}$", message = "El código debe estar en MAYÚSCULAS_CON_GUION_BAJO")
    private String code;

    @NotBlank(message = "El nombre del rol es obligatorio")
    @Size(max = 100)
    private String name;

    @Size(max = 255)
    private String description;

    @Builder.Default
    private List<String> permissions = new ArrayList<>();
}
