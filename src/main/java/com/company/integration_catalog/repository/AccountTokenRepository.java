package com.company.integration_catalog.repository;

import com.company.integration_catalog.model.entity.AccountToken;
import com.company.integration_catalog.model.enums.AccountTokenPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {
    @Query("select t.account.id from AccountToken t where t.tokenHash = :hash")
    Optional<Long> findAccountIdByTokenHash(@Param("hash") String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AccountToken t where t.tokenHash = :hash")
    Optional<AccountToken> lockByHash(@Param("hash") String hash);
    Optional<AccountToken> findFirstByAccountIdAndPurposeOrderByIssuedAtDesc(Long accountId, AccountTokenPurpose purpose);
}
