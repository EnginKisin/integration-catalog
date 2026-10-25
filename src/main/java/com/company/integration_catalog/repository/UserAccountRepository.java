package com.company.integration_catalog.repository;

import com.company.integration_catalog.model.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);
    Optional<UserAccount> findByEmail(String email);

    @Query(value = "SELECT * FROM user_accounts WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<UserAccount> lockById(@Param("id") Long id);

    @Query(value = "SELECT * FROM user_accounts WHERE role = 'ADMIN' ORDER BY id FOR UPDATE", nativeQuery = true)
    List<UserAccount> lockAdministrators();
}
