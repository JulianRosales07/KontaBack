package com.app.accounting.loan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegacyImportResultResponse {
    private int imported;
    private int skipped;
    @Builder.Default
    private List<String> messages = new ArrayList<>();
}
