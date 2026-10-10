package com.app.accounting.fine;

import com.app.accounting.common.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/** Tipo de multa con su valor vigente (p. ej. "Inasistencia a reunión" $5.000). */
@Entity
@Table(name = "fine_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FineType extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(length = 300)
    private String description;

    /** Valor que se aplica a las multas nuevas; cambiarlo no modifica las multas ya impuestas. */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
