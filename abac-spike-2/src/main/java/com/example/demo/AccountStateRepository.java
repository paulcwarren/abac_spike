package com.example.demo;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.versions.LockingAndVersioningRepository;

public interface AccountStateRepository extends JpaRepository<AccountState, Long>,
                                                LockingAndVersioningRepository<AccountState, Long>,
                                                QuerydslPredicateExecutor<AccountState> {

    List<AccountState> findByType(@Param("type") String type, Pageable pageable);

    // these do not work
    @Query("select d from AccountState d where d.type = :type")
    List<AccountState> byType(@Param("type") String type);

}
