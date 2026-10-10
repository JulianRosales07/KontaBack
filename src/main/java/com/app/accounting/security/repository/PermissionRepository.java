package com.app.accounting.security.repository;

import com.app.accounting.security.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    List<Permission> findAllByOrderByModuleAscCodeAsc();
    List<Permission> findByCodeIn(Collection<String> codes);
}
