package com.app.accounting.associate.entity;

public enum QuotaCategory {
    INCOME,          // Ingreso propio de la asociación (Clase 4)
    EQUITY_SAVING,   // Patrimonio / Aportes y Ahorro del socio (Clase 3)
    SOCIAL_FUND,     // Fondo mutual / Pasivo con destinación específica (Clase 2)
    LOAN_PAYMENT,    // Cuota integral de Préstamo o Crédito
    LOAN_PRINCIPAL,  // Amortización a Capital de Préstamo (Clase 1 - Activo 1365/1441)
    LOAN_INTEREST    // Intereses Corrientes de Préstamo (Clase 4 - Ingreso Financiero 4210)
}
