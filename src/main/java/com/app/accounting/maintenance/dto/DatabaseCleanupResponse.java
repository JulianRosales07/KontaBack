package com.app.accounting.maintenance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseCleanupResponse {
    private String message;
    private Map<String, Integer> deletedCounts;
}
