package com.app.accounting.billing.entity;

public enum DocumentStatus {
    PENDING,        // Pendiente de pago total
    PARTIALLY_PAID, // Pagado parcialmente (abono)
    PAID,           // Pagado en su totalidad
    CANCELLED       // Anulado
}
