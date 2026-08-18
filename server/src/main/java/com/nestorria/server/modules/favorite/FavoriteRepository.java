package com.nestorria.server.modules.favorite;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nestorria.server.common.datasource.ReadFromReplica;

public interface FavoriteRepository extends JpaRepository<Favorite, String> {

    boolean existsByUserIdAndPropertyId(String userId, String propertyId);

    Optional<Favorite> findByUserIdAndPropertyId(String userId, String propertyId);

    @ReadFromReplica
    List<Favorite> findByUserIdOrderByCreatedAtDesc(String userId);

    void deleteByUserIdAndPropertyId(String userId, String propertyId);

    @Modifying
    @Query(value = """
        INSERT INTO favorites (id, user_id, property_id, created_at, updated_at)
        VALUES (:id, :userId, :propertyId, :now, :now)
        ON CONFLICT (user_id, property_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("id") String id,
                       @Param("userId") String userId,
                       @Param("propertyId") String propertyId,
                       @Param("now") Instant now);
}
