package com.nestorria.server.modules.payment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    Optional<Invoice> findByBookingId(String bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") String id);

    @Query("SELECT i FROM Invoice i JOIN FETCH i.booking b JOIN FETCH b.user "
         + "WHERE b.user.id = :userId ORDER BY i.createdAt DESC")
    List<Invoice> findByUserIdOrderByCreatedAtDesc(@Param("userId") String userId);

    @Query("SELECT i FROM Invoice i JOIN FETCH i.booking b JOIN FETCH b.user "
         + "WHERE i.status = :status AND i.dueDate < :date")
    List<Invoice> findOverdueInvoicesWithBooking(
        @Param("status") InvoiceStatus status, @Param("date") LocalDate date);

    @Query("SELECT i FROM Invoice i JOIN FETCH i.booking b JOIN FETCH b.user "
         + "WHERE i.status = :status AND i.dueDate = :date")
    List<Invoice> findInvoicesDueOnDateWithBooking(
            @Param("status") InvoiceStatus status, @Param("date") LocalDate date);

    @Query("SELECT DISTINCT i FROM Invoice i "
         + "JOIN FETCH i.booking b JOIN FETCH b.user "
         + "LEFT JOIN FETCH i.paymentTransactions "
         + "WHERE b.user.id = :userId AND i.status IN ('PENDING', 'OVERDUE') "
         + "ORDER BY i.dueDate ASC")
    List<Invoice> findPayableInvoicesByUserId(@Param("userId") String userId);

    long countByStatus(InvoiceStatus status);
}
