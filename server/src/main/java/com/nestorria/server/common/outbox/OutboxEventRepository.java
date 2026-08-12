package com.nestorria.server.common.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("""
        SELECT e FROM OutboxEvent e
        WHERE e.status = :status
        AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now)
        ORDER BY e.createdAt ASC
        """)
    List<OutboxEvent> findProcessableEvents(
        @Param("status") OutboxEventStatus status,
        @Param("now") Instant now,
        Pageable pageable
    );

    long countByStatus(OutboxEventStatus status);
}
