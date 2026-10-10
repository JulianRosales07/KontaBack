package com.app.accounting.investment;

import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.puc.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvestmentService {

    private final InvestmentRepository repository;
    private final AccountService accountService;

    @Transactional(readOnly = true)
    public List<InvestmentResponse> list() {
        return repository.findAllWithAccount().stream().map(InvestmentResponse::of).toList();
    }

    @Transactional
    public InvestmentResponse create(InvestmentRequest request) {
        Investment investment = new Investment();
        apply(investment, request);
        return InvestmentResponse.of(repository.save(investment));
    }

    @Transactional
    public InvestmentResponse update(UUID id, InvestmentRequest request) {
        Investment investment = repository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("La inversión no existe"));
        apply(investment, request);
        return InvestmentResponse.of(repository.save(investment));
    }

    @Transactional
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    private void apply(Investment i, InvestmentRequest r) {
        if (!r.accountCode().trim().startsWith("12")) {
            throw new BusinessRuleException("La cuenta de una inversión debe pertenecer al grupo 12 (Inversiones)");
        }
        if (r.startDate() != null && r.maturityDate() != null && r.maturityDate().isBefore(r.startDate())) {
            throw new BusinessRuleException("El vencimiento no puede ser anterior a la fecha de constitución");
        }
        i.setEntityName(r.entityName().trim());
        i.setInstrumentType(r.instrumentType() == null || r.instrumentType().isBlank() ? "CDT" : r.instrumentType().trim().toUpperCase(Locale.ROOT));
        i.setInstrumentNumber(r.instrumentNumber());
        i.setAccount(accountService.getAccountEntityByCode(r.accountCode().trim()));
        i.setPrincipal(r.principal());
        i.setAccruedInterest(r.accruedInterest() != null ? r.accruedInterest() : BigDecimal.ZERO);
        i.setAnnualRate(r.annualRate());
        i.setStartDate(r.startDate());
        i.setMaturityDate(r.maturityDate());
        i.setStatus(r.status() == null || r.status().isBlank() ? "ACTIVE" : r.status().trim().toUpperCase(Locale.ROOT));
        i.setNotes(r.notes());
    }
}
