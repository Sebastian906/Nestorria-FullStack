package com.nestorria.server.modules.contract;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends JpaRepository<Contract, String> {

    boolean existsByBookingId(String bookingId);

    @Query("SELECT c FROM Contract c JOIN FETCH c.booking b WHERE b.property.id = :propertyId")
    List<Contract> findByPropertyId(@Param("propertyId") String propertyId);

    @Query("SELECT c FROM Contract c WHERE c.booking.id = :bookingId")
    Optional<Contract> findByBookingId(@Param("bookingId") String bookingId);

    @Query("SELECT c FROM Contract c JOIN FETCH c.booking WHERE c.booking.id IN :bookingIds")
    List<Contract> findByBookingIdIn(@Param("bookingIds") java.util.Collection<String> bookingIds);

    @Query("SELECT c FROM Contract c LEFT JOIN FETCH c.clauses LEFT JOIN FETCH c.signatures WHERE c.id = :id")
    Optional<Contract> findByIdWithDetails(@Param("id") String id);

    @Query("SELECT c FROM Contract c JOIN FETCH c.booking b JOIN FETCH b.property WHERE c.booking.user.id = :userId ORDER BY c.createdAt DESC")
    List<Contract> findSummaryByBookingUserId(@Param("userId") String userId);

    @Query("SELECT c FROM Contract c JOIN FETCH c.booking b JOIN FETCH b.property WHERE c.booking.agency.owner.id = :agencyOwnerId ORDER BY c.createdAt DESC")
    List<Contract> findSummaryByBookingAgencyOwnerId(@Param("agencyOwnerId") String agencyOwnerId);
}
