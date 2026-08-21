package com.nestorria.server.modules.review;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nestorria.server.common.datasource.ReadFromReplica;

public interface ReviewRepository extends JpaRepository<Review, String> {

    @ReadFromReplica
    List<Review> findByPropertyIdOrderByCreatedAtDesc(String propertyId);

    List<Review> findByUserIdOrderByCreatedAtDesc(String userId);

    boolean existsByUserIdAndPropertyId(String userId, String propertyId);

    Optional<Review> findByUserIdAndPropertyId(String userId, String propertyId);

    @ReadFromReplica
    @Query("""
        SELECT r.property.id, AVG(r.rating), COUNT(r)
        FROM Review r
        WHERE r.property.id IN :ids
        GROUP BY r.property.id
        """)
    List<Object[]> findRatingAggregatesByPropertyIds(@Param("ids") List<String> ids);
}
