package com.app.accounting.puc.dto.request;

import com.app.accounting.puc.entity.AccountNature;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountRequest {

    @NotBlank(message = "El código contable es obligatorio")
    @Size(min = 1, max = 20, message = "El código debe tener entre 1 y 20 dígitos")
    private String code;

    @NotBlank(message = "El nombre de la cuenta es obligatorio")
    @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
    private String name;

    @NotNull(message = "El nivel jerárquico es obligatorio")
    @Positive(message = "El nivel debe ser un número positivo")
    private Integer level;

    @NotNull(message = "La naturaleza (DEBIT/CREDIT) es obligatoria")
    private AccountNature nature;

    private UUID parentId;

    @Builder.Default
    private Boolean requiresThirdParty = false;

    @Builder.Default
    private Boolean isActive = true;
}
