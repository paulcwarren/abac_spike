package com.example.abac_spike;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AccountStateRepository extends JpaRepository<AccountState, Long> {

    List<AccountState> findByBrokerId(String tenantId);

    List<AccountState> findByType(String type, Pageable pageable);

    @Query("select d from AccountState d where d.type = :type")
    List<AccountState> byType(@Param("type") String type);

}
