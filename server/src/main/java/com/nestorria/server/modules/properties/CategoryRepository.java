package com.nestorria.server.modules.properties;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nestorria.server.common.datasource.ReadFromReplica;

public interface CategoryRepository extends JpaRepository<CategoryTree, Long> {

    @ReadFromReplica
    List<CategoryTree> findByParentIsNullOrderByName();

    @ReadFromReplica
    List<CategoryTree> findByParentIdOrderByName(Long parentId);

    @ReadFromReplica
    Optional<CategoryTree> findBySlug(String slug);

    boolean existsByParentId(Long parentId);
}
