package com.example.abac_spike;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByTenantId(String tenantId);

    List<Document> findByType(String type, Pageable pageable);

    @Query("select d from Document d where d.type = :type")
    List<Document> byType(@Param("type") String type);

}
