package com.app.accounting.party.service.impl;

import com.app.accounting.common.exception.DuplicateResourceException;
import com.app.accounting.party.dto.request.ThirdPartyRequest;
import com.app.accounting.party.dto.response.ThirdPartyResponse;
import com.app.accounting.party.entity.PersonType;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.exception.ThirdPartyNotFoundException;
import com.app.accounting.party.mapper.ThirdPartyMapper;
import com.app.accounting.party.repository.ThirdPartyRepository;
import com.app.accounting.party.service.ThirdPartyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ThirdPartyServiceImpl implements ThirdPartyService {

    private final ThirdPartyRepository thirdPartyRepository;
    private final ThirdPartyMapper thirdPartyMapper;

    @Override
    @Transactional
    public ThirdPartyResponse createThirdParty(ThirdPartyRequest request) {
        if (thirdPartyRepository.existsByIdentificationNumber(request.getIdentificationNumber())) {
            throw new DuplicateResourceException("Ya existe un tercero con el número de identificación: " + request.getIdentificationNumber());
        }

        ThirdParty thirdParty = thirdPartyMapper.toEntity(request);
        ThirdParty saved = thirdPartyRepository.save(thirdParty);
        return thirdPartyMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ThirdPartyResponse updateThirdParty(UUID id, ThirdPartyRequest request) {
        ThirdParty thirdParty = thirdPartyRepository.findById(id)
                .orElseThrow(() -> new ThirdPartyNotFoundException(id));

        if (!thirdParty.getIdentificationNumber().equals(request.getIdentificationNumber()) &&
                thirdPartyRepository.existsByIdentificationNumber(request.getIdentificationNumber())) {
            throw new DuplicateResourceException("Ya existe otro tercero con el número de identificación: " + request.getIdentificationNumber());
        }

        thirdPartyMapper.updateEntityFromRequest(request, thirdParty);
        ThirdParty updated = thirdPartyRepository.save(thirdParty);
        return thirdPartyMapper.toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public ThirdPartyResponse getThirdPartyById(UUID id) {
        ThirdParty thirdParty = thirdPartyRepository.findById(id)
                .orElseThrow(() -> new ThirdPartyNotFoundException(id));
        return thirdPartyMapper.toResponse(thirdParty);
    }

    @Override
    @Transactional(readOnly = true)
    public ThirdPartyResponse getThirdPartyByIdentification(String identificationNumber) {
        ThirdParty thirdParty = thirdPartyRepository.findByIdentificationNumber(identificationNumber)
                .orElseThrow(() -> new ThirdPartyNotFoundException(identificationNumber));
        return thirdPartyMapper.toResponse(thirdParty);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ThirdPartyResponse> getAllThirdParties(String query, Pageable pageable) {
        if (query != null && !query.isBlank()) {
            return thirdPartyRepository.searchThirdParties(query.trim(), pageable)
                    .map(thirdPartyMapper::toResponse);
        }
        return thirdPartyRepository.findAll(pageable)
                .map(thirdPartyMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ThirdParty getThirdPartyEntityByIdentification(String identificationNumber) {
        return thirdPartyRepository.findByIdentificationNumber(identificationNumber)
                .orElseThrow(() -> new ThirdPartyNotFoundException(identificationNumber));
    }

    @Override
    @Transactional(readOnly = true)
    public ThirdParty getThirdPartyEntityById(UUID id) {
        return thirdPartyRepository.findById(id)
                .orElseThrow(() -> new ThirdPartyNotFoundException(id));
    }

    @Override
    @Transactional
    public ThirdParty getOrCreateThirdParty(String identificationNumber, String fullName, PersonType type) {
        return thirdPartyRepository.findByIdentificationNumber(identificationNumber)
                .orElseGet(() -> {
                    ThirdParty newParty = ThirdParty.builder()
                            .identificationNumber(identificationNumber)
                            .fullName(fullName != null && !fullName.isBlank() ? fullName : "TERCERO " + identificationNumber)
                            .type(type != null ? type : PersonType.NATURAL)
                            .build();
                    return thirdPartyRepository.save(newParty);
                });
    }
}
