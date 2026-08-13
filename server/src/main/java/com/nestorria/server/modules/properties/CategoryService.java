package com.nestorria.server.modules.properties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.properties.dto.CategoryNode;
import com.nestorria.server.modules.properties.dto.CreateCategoryRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryNode> getTree() {
        List<CategoryTree> all = categoryRepository.findAll();
        return buildForest(all);
    }

    @Transactional(readOnly = true)
    public CategoryNode getCategoryById(Long id) {
        CategoryTree category = categoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
        List<CategoryTree> children = categoryRepository.findByParentIdOrderByName(id);
        return new CategoryNode(
            category.getId(),
            category.getName(),
            category.getSlug(),
            category.getDescription(),
            category.getLevel(),
            children.stream().map(CategoryNode::fromEntity).toList()
        );
    }

    @Transactional(readOnly = true)
    public List<CategoryNode> getDescendants(Long id) {
        CategoryTree root = categoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
        List<CategoryTree> all = categoryRepository.findAll();
        Map<Long, List<CategoryTree>> childrenMap = all.stream()
            .filter(c -> c.getParent() != null)
            .collect(Collectors.groupingBy(c -> c.getParent().getId()));
        return buildDescendants(root, childrenMap);
    }

    @Transactional(readOnly = true)
    public List<String> getCategoryPath(Long id) {
        CategoryTree category = categoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
        List<String> path = new ArrayList<>();
        CategoryTree current = category;
        while (current != null) {
            path.add(0, current.getName());
            current = current.getParent();
        }
        return path;
    }

    @Transactional
    public CategoryNode createCategory(CreateCategoryRequest request) {
        CategoryTree category = new CategoryTree();
        category.setName(request.name());
        category.setSlug(request.slug());
        category.setDescription(request.description());

        if (request.parentId() != null) {
            CategoryTree parent = categoryRepository.findById(request.parentId())
                .orElseThrow(() -> new ResourceNotFoundException("Parent category not found"));
            category.setParent(parent);
            category.setLevel(parent.getLevel() + 1);
        }

        CategoryTree saved = categoryRepository.save(category);
        return CategoryNode.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public Set<Long> getDescendantIds(Long categoryId) {
        List<CategoryTree> all = categoryRepository.findAll();
        Map<Long, List<CategoryTree>> childrenMap = all.stream()
            .filter(c -> c.getParent() != null)
            .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        Set<Long> ids = new HashSet<>();
        ids.add(categoryId);
        collectDescendants(categoryId, childrenMap, ids);
        return ids;
    }

    private void collectDescendants(Long parentId, Map<Long, List<CategoryTree>> childrenMap, Set<Long> ids) {
        List<CategoryTree> children = childrenMap.getOrDefault(parentId, List.of());
        for (CategoryTree child : children) {
            ids.add(child.getId());
            collectDescendants(child.getId(), childrenMap, ids);
        }
    }

    private List<CategoryNode> buildForest(List<CategoryTree> all) {
        Map<Long, CategoryNode> nodeMap = new LinkedHashMap<>();
        Map<Long, List<CategoryTree>> childrenMap = all.stream()
            .filter(c -> c.getParent() != null)
            .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        for (CategoryTree cat : all) {
            nodeMap.put(cat.getId(), CategoryNode.fromEntity(cat));
        }

        List<CategoryNode> roots = new ArrayList<>();
        for (CategoryTree cat : all) {
            if (cat.getParent() == null) {
                roots.add(buildNode(cat, nodeMap, childrenMap));
            }
        }
        return roots;
    }

    private CategoryNode buildNode(CategoryTree entity, Map<Long, CategoryNode> nodeMap,
                                   Map<Long, List<CategoryTree>> childrenMap) {
        List<CategoryTree> children = childrenMap.getOrDefault(entity.getId(), List.of());
        List<CategoryNode> childNodes = children.stream()
            .map(child -> buildNode(child, nodeMap, childrenMap))
            .toList();
        return new CategoryNode(
            entity.getId(),
            entity.getName(),
            entity.getSlug(),
            entity.getDescription(),
            entity.getLevel(),
            childNodes
        );
    }

    private List<CategoryNode> buildDescendants(CategoryTree root,
                                                 Map<Long, List<CategoryTree>> childrenMap) {
        List<CategoryNode> result = new ArrayList<>();
        List<CategoryTree> children = childrenMap.getOrDefault(root.getId(), List.of());
        for (CategoryTree child : children) {
            result.add(new CategoryNode(
                child.getId(),
                child.getName(),
                child.getSlug(),
                child.getDescription(),
                child.getLevel(),
                buildDescendants(child, childrenMap)
            ));
        }
        return result;
    }
}
