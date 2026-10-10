package com.app.accounting.security.repository;

import com.app.accounting.security.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCode(String code);
    List<Role> findByCodeIn(Collection<String> codes);
    List<Role> findAllByOrderByNameAsc();
    boolean existsByCode(String code);

    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.code = :code AND u.enabled = true")
    long countEnabledUsersWithRole(@Param("code") String code);
}
