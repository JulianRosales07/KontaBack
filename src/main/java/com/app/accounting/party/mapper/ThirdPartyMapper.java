package com.app.accounting.party.mapper;

import com.app.accounting.party.dto.request.ThirdPartyRequest;
import com.app.accounting.party.dto.response.ThirdPartyResponse;
import com.app.accounting.party.entity.ThirdParty;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface ThirdPartyMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    ThirdParty toEntity(ThirdPartyRequest request);

    ThirdPartyResponse toResponse(ThirdParty thirdParty);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntityFromRequest(ThirdPartyRequest request, @MappingTarget ThirdParty thirdParty);
}
