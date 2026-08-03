package com.nestorria.server.modules.payment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, String> {

    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.invoice.id = :invoiceId ORDER BY pt.createdAt DESC")
    List<PaymentTransaction> findByInvoiceIdOrderByCreatedAtDesc(@Param("invoiceId") String invoiceId);

    Optional<PaymentTransaction> findByGatewayReference(String gatewayReference);

    List<PaymentTransaction> findByStatus(TransactionStatus status);
}
