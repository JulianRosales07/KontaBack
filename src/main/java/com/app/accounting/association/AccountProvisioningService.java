package com.app.accounting.association;

import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.entity.AccountNature;
import com.app.accounting.puc.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Garantiza que existan las cuentas del mapeo de la asociación (y su cadena de padres) sin tocar las existentes.
 * Es idempotente: si la cuenta ya existe se devuelve tal cual.
 */
@Service
@RequiredArgsConstructor
public class AccountProvisioningService {

    private final AccountRepository accountRepository;

    /** Devuelve la cuenta, creándola junto con sus padres faltantes. Retorna en {@code created} los códigos creados. */
    @Transactional
    public Account ensure(String code, List<String> created) {
        return ensure(code, null, created);
    }

    /**
     * Igual que {@link #ensure(String, List)} pero permite un nombre propio para una cuenta que no está en
     * {@link AssociationChart#DEFINITIONS} (por ejemplo el auxiliar de un banco leído del informe).
     */
    @Transactional
    public Account ensure(String code, String nameIfNew, List<String> created) {
        Account existing = accountRepository.findByCode(code).orElse(null);
        if (existing != null) {
            return existing;
        }
        AssociationChart.Definition def = AssociationChart.DEFINITIONS.get(code);
        String name = nameIfNew != null ? nameIfNew : def != null ? def.name() : null;
        if (name == null) {
            throw new BusinessRuleException("La cuenta PUC " + code + " no existe y no hay una definición para crearla");
        }
        String parentCode = AssociationChart.parentOf(code);
        Account parent = parentCode != null ? ensure(parentCode, created) : null;
        Account account = Account.builder()
                .code(code)
                .name(name.toUpperCase())
                .level(AssociationChart.levelOf(code))
                .nature(AssociationChart.isDebitNature(code) ? AccountNature.DEBIT : AccountNature.CREDIT)
                .parent(parent)
                .requiresThirdParty(def != null && def.requiresThirdParty())
                .isActive(true)
                .build();
        Account saved = accountRepository.save(account);
        if (created != null) {
            created.add(code);
        }
        return saved;
    }

    /** Renombres aprobados por la contadora (el código no cambia). También los aplica la migración V11. */
    static final java.util.Map<String, String> APPROVED_NAMES = java.util.Map.of(
            AssociationChart.SOLIDARITY_FUND, "FONDO DE SOLIDARIDAD",
            AssociationChart.TOURISM_FUND, "FONDO DE TURISMO Y RECREACION",
            AssociationChart.LOANS_TO_ASSOCIATES, "PRESTAMOS A ASOCIADOS");

    /**
     * Asegura todas las cuentas del mapeo y los nombres aprobados (en bases nuevas el PUC completo se carga
     * después de las migraciones, por eso el renombre de V11 puede no haberlas alcanzado).
     */
    @Transactional
    public List<String> ensureAll() {
        List<String> created = new ArrayList<>();
        for (String code : AssociationChart.DEFINITIONS.keySet()) {
            ensure(code, created);
        }
        APPROVED_NAMES.forEach((code, name) -> accountRepository.findByCode(code).ifPresent(a -> {
            if (!name.equals(a.getName())) {
                a.setName(name);
                accountRepository.save(a);
            }
        }));
        return created;
    }
}
