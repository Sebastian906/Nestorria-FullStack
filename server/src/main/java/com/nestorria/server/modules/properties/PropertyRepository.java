package com.nestorria.server.modules.properties;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropertyRepository extends JpaRepository<Property, String> {

    List<Property> findByIsAvailableTrue();

    @Query("SELECT p FROM Property p WHERE p.agency.id = :agencyId")
    List<Property> findByAgencyId(@Param("agencyId") String agencyId);
}
