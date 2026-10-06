package com.app.accounting.puc.service;

import com.app.accounting.puc.dto.request.AccountRequest;
import com.app.accounting.puc.dto.response.AccountResponse;
import com.app.accounting.puc.entity.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AccountService {
    AccountResponse createAccount(AccountRequest request);
    AccountResponse updateAccount(UUID id, AccountRequest request);
    AccountResponse getAccountById(UUID id);
    AccountResponse getAccountByCode(String code);
    Page<AccountResponse> getAllAccounts(Pageable pageable);
    List<AccountResponse> getAllActiveAccounts();
    Account getAccountEntityByCode(String code);
    Account getAccountEntityById(UUID id);
    com.app.accounting.puc.dto.response.PucImportResultResponse importPucFromExcel(java.io.InputStream inputStream);
    com.app.accounting.puc.dto.response.PucImportResultResponse importDefaultPuc();
}
