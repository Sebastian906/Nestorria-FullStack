package com.nestorria.server.modules.favorite;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nestorria.server.common.datasource.ReadFromReplica;

public interface FavoriteRepository extends JpaRepository<Favorite, String> {

    boolean existsByUserIdAndPropertyId(String userId, String propertyId);

    Optional<Favorite> findByUserIdAndPropertyId(String userId, String propertyId);

    @ReadFromReplica
    List<Favorite> findByUserIdOrderByCreatedAtDesc(String userId);

    void deleteByUserIdAndPropertyId(String userId, String propertyId);
}
