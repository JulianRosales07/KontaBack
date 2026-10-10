package com.app.accounting.associate.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Importación única de terceros/asociados guardados en el navegador. Se identifica por número de documento. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssociateImportRequest {

    @NotEmpty(message = "No hay registros para importar")
    @Valid
    @Builder.Default
    private List<Item> items = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @NotBlank
        private String identificationNumber;
        @NotBlank
        private String fullName;
        private String dv;
        private String idType;
        private String email;
        private String phone;
        private String address;
        private String classification;
        private String status;
    }
}
