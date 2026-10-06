package com.app.accounting.party.service;

import com.app.accounting.party.dto.request.ThirdPartyRequest;
import com.app.accounting.party.dto.response.ThirdPartyResponse;
import com.app.accounting.party.entity.PersonType;
import com.app.accounting.party.entity.ThirdParty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ThirdPartyService {
    ThirdPartyResponse createThirdParty(ThirdPartyRequest request);
    ThirdPartyResponse updateThirdParty(UUID id, ThirdPartyRequest request);
    ThirdPartyResponse getThirdPartyById(UUID id);
    ThirdPartyResponse getThirdPartyByIdentification(String identificationNumber);
    Page<ThirdPartyResponse> getAllThirdParties(String query, Pageable pageable);
    ThirdParty getThirdPartyEntityByIdentification(String identificationNumber);
    ThirdParty getThirdPartyEntityById(UUID id);
    ThirdParty getOrCreateThirdParty(String identificationNumber, String fullName, PersonType type);
}
