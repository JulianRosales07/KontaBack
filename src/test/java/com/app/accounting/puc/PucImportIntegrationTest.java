package com.app.accounting.puc;

import com.app.accounting.puc.dto.response.PucImportResultResponse;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.entity.AccountNature;
import com.app.accounting.puc.service.AccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PucImportIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Test
    @Transactional
    @DisplayName("Debe importar exitosamente las 2.521 cuentas del PUC desde Docs/PUC_Cuentas_Comerciantes.xlsx")
    void shouldImportAllPucAccountsFromExcel() {
        PucImportResultResponse result = accountService.importDefaultPuc();

        assertThat(result).isNotNull();
        assertThat(result.getTotalProcessed()).isEqualTo(2521);
        assertThat(result.getTotalCreated() + result.getTotalUpdated()).isEqualTo(2521);

        // Validar jerarquía y propiedades de cuentas clave
        Account activo = accountService.getAccountEntityByCode("1");
        assertThat(activo.getName()).isEqualToIgnoringCase("ACTIVO");
        assertThat(activo.getLevel()).isEqualTo(1);
        assertThat(activo.getNature()).isEqualTo(AccountNature.DEBIT);
        assertThat(activo.getParent()).isNull();

        Account disponible = accountService.getAccountEntityByCode("11");
        assertThat(disponible.getName()).isEqualToIgnoringCase("DISPONIBLE");
        assertThat(disponible.getLevel()).isEqualTo(2);
        assertThat(disponible.getParent()).isNotNull();
        assertThat(disponible.getParent().getCode()).isEqualTo("1");

        Account caja = accountService.getAccountEntityByCode("1105");
        assertThat(caja.getName()).isEqualToIgnoringCase("CAJA");
        assertThat(caja.getLevel()).isEqualTo(3);
        assertThat(caja.getParent()).isNotNull();
        assertThat(caja.getParent().getCode()).isEqualTo("11");

        Account cajaGeneral = accountService.getAccountEntityByCode("110505");
        assertThat(cajaGeneral.getName()).isEqualToIgnoringCase("CAJA GENERAL");
        assertThat(cajaGeneral.getLevel()).isEqualTo(4);
        assertThat(cajaGeneral.getParent()).isNotNull();
        assertThat(cajaGeneral.getParent().getCode()).isEqualTo("1105");

        Account pasivo = accountService.getAccountEntityByCode("2");
        assertThat(pasivo.getName()).isEqualToIgnoringCase("PASIVO");
        assertThat(pasivo.getNature()).isEqualTo(AccountNature.CREDIT);

        Account nacionales = accountService.getAccountEntityByCode("2205");
        assertThat(nacionales.getName()).isEqualToIgnoringCase("NACIONALES");
        assertThat(nacionales.getLevel()).isEqualTo(3);
        assertThat(nacionales.getNature()).isEqualTo(AccountNature.CREDIT);
        assertThat(nacionales.getParent()).isNotNull();
        assertThat(nacionales.getParent().getCode()).isEqualTo("22");
    }
}
