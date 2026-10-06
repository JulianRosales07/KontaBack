package com.app.accounting.puc.mapper;

import com.app.accounting.puc.dto.request.AccountRequest;
import com.app.accounting.puc.dto.response.AccountResponse;
import com.app.accounting.puc.entity.Account;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface AccountMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Account toEntity(AccountRequest request);

    @Mapping(source = "parent.id", target = "parentId")
    @Mapping(source = "parent.code", target = "parentCode")
    @Mapping(source = "parent.name", target = "parentName")
    AccountResponse toResponse(Account account);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntityFromRequest(AccountRequest request, @MappingTarget Account account);
}
