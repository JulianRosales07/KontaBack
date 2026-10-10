package com.app.accounting.party.dto.response;

import com.app.accounting.party.entity.PersonType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyResponse {
    private UUID id;
    private String identificationNumber;
    private String dv;
    private String fullName;
    private String email;
    private String phone;
    private PersonType type;

    private String idType;
    private String address;
    private String classification;
    private boolean associate;
    private String associateCode;
    private String status;
}
