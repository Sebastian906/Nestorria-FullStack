package com.nestorria.server.modules.agency;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgencyRepository extends JpaRepository<Agency, String> {
    boolean existsByOwnerId(String ownerId);
    Optional<Agency> findByOwnerId(String ownerId);
}
