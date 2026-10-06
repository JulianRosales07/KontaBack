package com.app.accounting.puc.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PucImportResultResponse {
    private int totalProcessed;
    private int totalCreated;
    private int totalUpdated;
    private String message;
}
