package com.app.accounting.puc.dto.response;

import com.app.accounting.puc.entity.AccountNature;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {
    private UUID id;
    private String code;
    private String name;
    private Integer level;
    private AccountNature nature;
    private UUID parentId;
    private String parentCode;
    private String parentName;
    private Boolean requiresThirdParty;
    private Boolean isActive;
}
