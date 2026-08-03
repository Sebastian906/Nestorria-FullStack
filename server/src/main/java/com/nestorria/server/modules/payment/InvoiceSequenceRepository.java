package com.nestorria.server.modules.payment;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, Long> {

    Optional<InvoiceSequence> findByYear(int year);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InvoiceSequence s WHERE s.year = :year")
    Optional<InvoiceSequence> findByYearForUpdate(@Param("year") int year);
}
