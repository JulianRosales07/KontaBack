package com.app.accounting.party.entity;

import com.app.accounting.common.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "third_parties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThirdParty extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "identification_number", nullable = false, unique = true, length = 30)
    private String identificationNumber;

    @Column(length = 2)
    private String dv;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(length = 100)
    private String email;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PersonType type;

    /** Tipo de documento de identificación (CC, NIT, CE, TI, PASAPORTE...). */
    @Column(name = "id_type", length = 40)
    private String idType;

    @Column(length = 255)
    private String address;

    /** Clasificación libre: Asociado, Cliente, Proveedor, Empleado, Banco, Entidad. */
    @Column(length = 30)
    private String classification;

    @Column(name = "is_associate", nullable = false)
    @Builder.Default
    private boolean associate = false;

    @Column(name = "associate_code", length = 30)
    private String associateCode;

    /** ACTIVE | INACTIVE */
    @Column(nullable = false, length = 15)
    @Builder.Default
    private String status = "ACTIVE";
}
