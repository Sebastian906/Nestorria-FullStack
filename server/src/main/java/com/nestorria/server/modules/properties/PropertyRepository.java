package com.nestorria.server.modules.properties;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nestorria.server.common.datasource.ReadFromReplica;

public interface PropertyRepository extends JpaRepository<Property, String> {

    @ReadFromReplica
    @Query("SELECT DISTINCT p FROM Property p LEFT JOIN FETCH p.category JOIN FETCH p.agency WHERE p.isAvailable = true")
    List<Property> findByIsAvailableTrue();

    @Query("SELECT DISTINCT p FROM Property p LEFT JOIN FETCH p.category JOIN FETCH p.agency WHERE p.agency.id = :agencyId")
    List<Property> findByAgencyId(@Param("agencyId") String agencyId);
}
