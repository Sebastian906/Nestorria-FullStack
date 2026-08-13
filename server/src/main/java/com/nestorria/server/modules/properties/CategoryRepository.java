package com.nestorria.server.modules.properties;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoryTree, Long> {

    List<CategoryTree> findByParentIsNullOrderByName();

    List<CategoryTree> findByParentIdOrderByName(Long parentId);

    Optional<CategoryTree> findBySlug(String slug);

    boolean existsByParentId(Long parentId);
}
