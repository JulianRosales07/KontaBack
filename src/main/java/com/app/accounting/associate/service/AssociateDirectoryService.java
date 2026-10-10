package com.app.accounting.associate.service;

import com.app.accounting.associate.dto.request.AssociateImportRequest;
import com.app.accounting.associate.dto.response.AssociateSummaryResponse;
import com.app.accounting.associate.entity.QuotaCategory;
import com.app.accounting.associate.repository.AssociateReceiptRepository;
import com.app.accounting.loan.dto.response.LegacyImportResultResponse;
import com.app.accounting.loan.entity.Loan;
import com.app.accounting.loan.entity.LoanStatus;
import com.app.accounting.loan.repository.LoanRepository;
import com.app.accounting.party.entity.PersonType;
import com.app.accounting.party.entity.ThirdParty;
import com.app.accounting.party.repository.ThirdPartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

/**
 * Directorio de asociados con búsqueda global y filtros, y consolidado de aportes / ahorros (cuentas por pagar).
 * Todos los saldos se calculan desde las mismas fuentes que usa la contabilidad: recibos de asociados y préstamos.
 */
@Service
@RequiredArgsConstructor
public class AssociateDirectoryService {

    private final ThirdPartyRepository thirdPartyRepository;
    private final AssociateReceiptRepository receiptRepository;
    private final LoanRepository loanRepository;

    public record Filters(String query, String status, String idType, Boolean associatesOnly,
                          Boolean hasLoan, Boolean hasSavings, Boolean hasContributions) {
    }

    @Transactional(readOnly = true)
    public List<AssociateSummaryResponse> search(Filters f) {
        List<AssociateSummaryResponse> all = buildSummaries();
        List<String> tokens = tokenize(f.query());

        return all.stream()
                .filter(a -> f.associatesOnly() == null || !f.associatesOnly() || a.isAssociate())
                .filter(a -> isBlank(f.status()) || f.status().equalsIgnoreCase(a.getStatus()))
                .filter(a -> isBlank(f.idType()) || f.idType().equalsIgnoreCase(nullToEmpty(a.getIdType())))
                .filter(a -> f.hasLoan() == null || f.hasLoan() == (a.getActiveLoans() > 0))
                .filter(a -> f.hasSavings() == null || f.hasSavings() == (a.getSavings().signum() > 0))
                .filter(a -> f.hasContributions() == null || f.hasContributions() == (a.getContributions().signum() > 0))
                .filter(a -> matches(a, tokens))
                .sorted(Comparator.comparing(a -> normalize(a.getFullName())))
                .toList();
    }

    /** Cuentas por pagar: asociados con aportes o ahorros acumulados. */
    @Transactional(readOnly = true)
    public List<AssociateSummaryResponse> payables(String query) {
        List<String> tokens = tokenize(query);
        return buildSummaries().stream()
                .filter(a -> a.getContributions().signum() > 0 || a.getSavings().signum() > 0)
                .filter(a -> matches(a, tokens))
                .sorted(Comparator.comparing(AssociateSummaryResponse::getPayableBalance).reversed())
                .toList();
    }

    @Transactional
    public LegacyImportResultResponse importAssociates(AssociateImportRequest request) {
        LegacyImportResultResponse result = LegacyImportResultResponse.builder().build();
        for (AssociateImportRequest.Item item : request.getItems()) {
            String idNumber = item.getIdentificationNumber().trim();
            Optional<ThirdParty> existing = thirdPartyRepository.findByIdentificationNumber(idNumber);
            boolean isAssociate = item.getClassification() == null || "Asociado".equalsIgnoreCase(item.getClassification());
            if (existing.isPresent()) {
                ThirdParty p = existing.get();
                boolean changed = false;
                if (p.getEmail() == null && item.getEmail() != null) { p.setEmail(item.getEmail()); changed = true; }
                if (p.getPhone() == null && item.getPhone() != null) { p.setPhone(item.getPhone()); changed = true; }
                if (p.getAddress() == null && item.getAddress() != null) { p.setAddress(item.getAddress()); changed = true; }
                if (p.getIdType() == null && item.getIdType() != null) { p.setIdType(item.getIdType()); changed = true; }
                if (p.getClassification() == null && item.getClassification() != null) { p.setClassification(item.getClassification()); changed = true; }
                if (isAssociate && !p.isAssociate()) { p.setAssociate(true); changed = true; }
                if (changed) {
                    thirdPartyRepository.save(p);
                }
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            boolean juridica = item.getIdType() != null && item.getIdType().toUpperCase().contains("NIT");
            thirdPartyRepository.save(ThirdParty.builder()
                    .identificationNumber(idNumber)
                    .dv(item.getDv() != null && item.getDv().length() <= 2 ? item.getDv() : null)
                    .fullName(item.getFullName().trim())
                    .email(item.getEmail())
                    .phone(item.getPhone())
                    .address(item.getAddress())
                    .idType(item.getIdType())
                    .classification(item.getClassification())
                    .associate(isAssociate)
                    .status("Inactivo".equalsIgnoreCase(item.getStatus()) || "INACTIVE".equalsIgnoreCase(item.getStatus()) ? "INACTIVE" : "ACTIVE")
                    .type(juridica ? PersonType.JURIDICA : PersonType.NATURAL)
                    .build());
            result.setImported(result.getImported() + 1);
        }
        result.getMessages().add(result.getImported() + " terceros importados, " + result.getSkipped() + " ya existían");
        return result;
    }

    // ------------------------------------------------------------------ internals

    private List<AssociateSummaryResponse> buildSummaries() {
        Map<UUID, BigDecimal> contributions = new HashMap<>();
        Map<UUID, BigDecimal> savings = new HashMap<>();
        Map<UUID, Long> movements = new HashMap<>();
        Map<UUID, LocalDate> lastMovement = new HashMap<>();

        for (Object[] row : receiptRepository.aggregateByThirdPartyAndCategory()) {
            UUID partyId = (UUID) row[0];
            QuotaCategory category = (QuotaCategory) row[1];
            BigDecimal sum = (BigDecimal) row[2];
            long count = ((Number) row[3]).longValue();
            LocalDate last = (LocalDate) row[4];
            if (category == QuotaCategory.EQUITY_SAVING) {
                contributions.merge(partyId, sum, BigDecimal::add);
                movements.merge(partyId, count, Math::max);
            } else if (category == QuotaCategory.SAVINGS) {
                savings.merge(partyId, sum, BigDecimal::add);
                movements.merge(partyId, count, Math::max);
            }
            if (last != null) {
                lastMovement.merge(partyId, last, (a, b) -> a.isAfter(b) ? a : b);
            }
        }

        Map<UUID, Integer> activeLoans = new HashMap<>();
        Map<UUID, BigDecimal> loanBalance = new HashMap<>();
        for (Loan loan : loanRepository.findByStatusInWithDetails(List.of(LoanStatus.ACTIVE))) {
            UUID partyId = loan.getThirdParty().getId();
            activeLoans.merge(partyId, 1, Integer::sum);
            loanBalance.merge(partyId, loan.getOutstandingPrincipal(), BigDecimal::add);
        }

        return thirdPartyRepository.findAll().stream().map(p -> {
            BigDecimal c = contributions.getOrDefault(p.getId(), BigDecimal.ZERO);
            BigDecimal s = savings.getOrDefault(p.getId(), BigDecimal.ZERO);
            return AssociateSummaryResponse.builder()
                    .id(p.getId())
                    .associateCode(p.getAssociateCode())
                    .fullName(p.getFullName())
                    .idType(p.getIdType())
                    .identificationNumber(p.getIdentificationNumber())
                    .email(p.getEmail())
                    .phone(p.getPhone())
                    .address(p.getAddress())
                    .classification(p.getClassification())
                    .associate(p.isAssociate())
                    .status(p.getStatus())
                    .activeLoans(activeLoans.getOrDefault(p.getId(), 0))
                    .loansBalance(loanBalance.getOrDefault(p.getId(), BigDecimal.ZERO))
                    .contributions(c)
                    .savings(s)
                    .payableBalance(c.add(s))
                    .movements(movements.getOrDefault(p.getId(), 0L))
                    .lastMovementDate(lastMovement.get(p.getId()))
                    .build();
        }).toList();
    }

    /** Cada palabra buscada debe aparecer en alguno de los campos (sin distinguir tildes ni mayúsculas). */
    private static boolean matches(AssociateSummaryResponse a, List<String> tokens) {
        if (tokens.isEmpty()) {
            return true;
        }
        String haystack = normalize(String.join(" ", Stream.of(
                a.getFullName(), a.getIdentificationNumber(), a.getEmail(), a.getPhone(), a.getAssociateCode())
                .filter(Objects::nonNull).toList()));
        String digits = haystack.replaceAll("[^0-9 ]", "");
        return tokens.stream().allMatch(t -> haystack.contains(t) || (!t.isBlank() && t.chars().allMatch(Character::isDigit) && digits.contains(t)));
    }

    private static List<String> tokenize(String query) {
        if (isBlank(query)) {
            return List.of();
        }
        return Arrays.stream(normalize(query).split("\\s+")).filter(t -> !t.isBlank()).toList();
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
