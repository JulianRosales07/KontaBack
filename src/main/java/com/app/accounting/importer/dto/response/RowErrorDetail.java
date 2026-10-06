package com.app.accounting.importer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RowErrorDetail {
    private int rowNumber;
    private String field;
    private String message;
    private String rejectedValue;
}
