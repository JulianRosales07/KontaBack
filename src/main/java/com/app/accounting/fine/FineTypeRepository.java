package com.app.accounting.fine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FineTypeRepository extends JpaRepository<FineType, UUID> {

    List<FineType> findAllByOrderByActiveDescNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
