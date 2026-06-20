package com.nestorria.server.modules.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nestorria.server.modules.properties.Property;

import jakarta.persistence.LockModeType;

public interface BookingRepository extends JpaRepository<Booking, String> {

    @Query("""
        SELECT COUNT(b) > 0 FROM Booking b
        WHERE b.property.id = :propertyId
        AND b.status <> :excludedStatus
        AND b.checkInDate < :checkOutDate
        AND b.checkOutDate > :checkInDate
        """)

    boolean existsOverlappingBooking(
        @Param("propertyId") String propertyId,
        @Param("checkInDate") LocalDate checkInDate,
        @Param("checkOutDate") LocalDate checkOutDate,
        @Param("excludedStatus") BookingStatus excludedStatus
    );

    @Query("SELECT b FROM Booking b WHERE b.user.id = :userId ORDER BY b.createdAt DESC")
    List<Booking> findByUserId(@Param("userId") String userId);

    @Query("SELECT b FROM Booking b JOIN FETCH b.property JOIN FETCH b.agency WHERE b.agency.id = :agencyId ORDER BY b.createdAt DESC")
    List<Booking> findByAgencyId(@Param("agencyId") String agencyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Property p WHERE p.id = :id")
    Optional<Property> findByIdWithLock(@Param("id") String id);

}
