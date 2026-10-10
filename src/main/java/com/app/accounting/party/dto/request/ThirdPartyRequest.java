package com.app.accounting.party.dto.request;

import com.app.accounting.party.entity.PersonType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyRequest {

    @NotBlank(message = "El número de identificación (NIT o Cédula) es obligatorio")
    @Size(max = 30, message = "La identificación no puede superar 30 caracteres")
    private String identificationNumber;

    @Size(max = 2, message = "El dígito de verificación (DV) no puede superar 2 caracteres")
    private String dv;

    @NotBlank(message = "El nombre o razón social es obligatorio")
    @Size(max = 200, message = "El nombre completo no puede superar 200 caracteres")
    private String fullName;

    @Email(message = "El formato de correo electrónico es inválido")
    private String email;

    @Size(max = 30, message = "El teléfono no puede superar 30 caracteres")
    private String phone;

    @NotNull(message = "El tipo de persona (NATURAL o JURIDICA) es obligatorio")
    private PersonType type;

    private String idType;
    private String address;
    private String classification;
    private Boolean associate;
    private String associateCode;
    private String status;
}
