package com.app.accounting.puc.service.impl;

import com.app.accounting.puc.dto.request.AccountRequest;
import com.app.accounting.puc.dto.response.AccountResponse;
import com.app.accounting.puc.dto.response.PucImportResultResponse;
import com.app.accounting.puc.entity.Account;
import com.app.accounting.puc.entity.AccountNature;
import com.app.accounting.puc.exception.AccountNotFoundException;
import com.app.accounting.puc.mapper.AccountMapper;
import com.app.accounting.puc.repository.AccountRepository;
import com.app.accounting.puc.service.AccountService;
import com.app.accounting.common.exception.BusinessRuleException;
import com.app.accounting.common.exception.DuplicateResourceException;
import com.app.accounting.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    @Override
    @Transactional
    public AccountResponse createAccount(AccountRequest request) {
        if (accountRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Ya existe una cuenta con el código: " + request.getCode());
        }

        Account account = accountMapper.toEntity(request);

        if (request.getParentId() != null) {
            Account parent = accountRepository.findById(request.getParentId())
                    .orElseThrow(() -> new AccountNotFoundException(request.getParentId()));
            account.setParent(parent);
        }

        Account saved = accountRepository.save(account);
        return accountMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AccountResponse updateAccount(UUID id, AccountRequest request) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

        if (!account.getCode().equals(request.getCode()) && accountRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Ya existe otra cuenta con el código: " + request.getCode());
        }

        accountMapper.updateEntityFromRequest(request, account);

        if (request.getParentId() != null) {
            Account parent = accountRepository.findById(request.getParentId())
                    .orElseThrow(() -> new AccountNotFoundException(request.getParentId()));
            account.setParent(parent);
        } else {
            account.setParent(null);
        }

        Account updated = accountRepository.save(account);
        return accountMapper.toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountByCode(String code) {
        Account account = accountRepository.findByCode(code)
                .orElseThrow(() -> new AccountNotFoundException(code));
        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountResponse> getAllAccounts(Pageable pageable) {
        return accountRepository.findAll(pageable)
                .map(accountMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAllActiveAccounts() {
        return accountRepository.findByIsActiveTrueOrderByCodeAsc().stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Account getAccountEntityByCode(String code) {
        return accountRepository.findByCode(code)
                .orElseThrow(() -> new AccountNotFoundException(code));
    }

    @Override
    @Transactional(readOnly = true)
    public Account getAccountEntityById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Override
    @Transactional
    public PucImportResultResponse importDefaultPuc() {
        File file = new File("Docs/PUC_Cuentas_Comerciantes.xlsx");
        if (!file.exists()) {
            throw new ResourceNotFoundException("El archivo predeterminado Docs/PUC_Cuentas_Comerciantes.xlsx no existe en el servidor");
        }
        try (InputStream is = new FileInputStream(file)) {
            return importPucFromExcel(is);
        } catch (Exception e) {
            throw new BusinessRuleException("Error al importar el archivo PUC predeterminado: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public PucImportResultResponse importPucFromExcel(InputStream inputStream) {
        int created = 0;
        int updated = 0;
        int processed = 0;

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            Map<String, Account> accountCache = new HashMap<>();
            for (Account acc : accountRepository.findAll()) {
                accountCache.put(acc.getCode(), acc);
            }

            Map<Integer, List<PucRowData>> levelBuckets = new TreeMap<>();
            for (int lvl = 1; lvl <= 5; lvl++) {
                levelBuckets.put(lvl, new ArrayList<>());
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String code = formatter.formatCellValue(row.getCell(0)).trim();
                String name = formatter.formatCellValue(row.getCell(1)).trim();
                String natureStr = formatter.formatCellValue(row.getCell(3)).trim();

                if (code.isBlank() || name.isBlank()) continue;

                int level;
                if (code.length() == 1) level = 1;
                else if (code.length() == 2) level = 2;
                else if (code.length() == 4) level = 3;
                else if (code.length() == 6) level = 4;
                else level = 5;

                AccountNature nature = (natureStr.toLowerCase().contains("crédito") || natureStr.toLowerCase().contains("credito"))
                        ? AccountNature.CREDIT
                        : AccountNature.DEBIT;

                boolean requiresThirdParty = (code.startsWith("13") || code.startsWith("22") || code.startsWith("23") ||
                        code.startsWith("24") || code.startsWith("25") || code.startsWith("51") || code.startsWith("52")) && code.length() >= 6;

                String parentCode = null;
                if (level == 2) parentCode = code.substring(0, 1);
                else if (level == 3) parentCode = code.substring(0, 2);
                else if (level == 4) parentCode = code.substring(0, 4);
                else if (level == 5) parentCode = code.substring(0, 6);

                levelBuckets.get(level).add(new PucRowData(code, name, level, nature, parentCode, requiresThirdParty));
            }

            for (Map.Entry<Integer, List<PucRowData>> entry : levelBuckets.entrySet()) {
                List<Account> toSave = new ArrayList<>();
                for (PucRowData data : entry.getValue()) {
                    processed++;
                    Account account = accountCache.get(data.code);
                    boolean isNew = (account == null);

                    if (isNew) {
                        account = new Account();
                        account.setCode(data.code);
                        account.setIsActive(true);
                        created++;
                    } else {
                        updated++;
                    }

                    account.setName(data.name);
                    account.setLevel(data.level);
                    account.setNature(data.nature);
                    account.setRequiresThirdParty(data.requiresThirdParty);

                    if (data.parentCode != null) {
                        Account parent = accountCache.get(data.parentCode);
                        account.setParent(parent);
                    } else {
                        account.setParent(null);
                    }

                    toSave.add(account);
                }

                List<Account> savedBatch = accountRepository.saveAll(toSave);
                for (Account saved : savedBatch) {
                    accountCache.put(saved.getCode(), saved);
                }
            }

            return PucImportResultResponse.builder()
                    .totalProcessed(processed)
                    .totalCreated(created)
                    .totalUpdated(updated)
                    .message(String.format("Importación PUC completada con éxito. %d cuentas procesadas (%d creadas, %d actualizadas).",
                            processed, created, updated))
                    .build();

        } catch (Exception ex) {
            throw new BusinessRuleException("Error importando cuentas PUC desde Excel: " + ex.getMessage(), ex);
        }
    }

    private record PucRowData(
            String code,
            String name,
            int level,
            AccountNature nature,
            String parentCode,
            boolean requiresThirdParty
    ) {}
}
