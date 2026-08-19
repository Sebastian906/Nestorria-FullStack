package com.nestorria.server.modules.properties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.algorithm.FiniteAutomaton;
import com.nestorria.server.common.exception.BadRequestException;
import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.properties.dto.CategoryNode;
import com.nestorria.server.modules.properties.dto.CreateCategoryRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    // AFD para ^[a-z0-9]+(?:-[a-z0-9]+)*$ — misma regla que @Pattern del DTO.
    private static final FiniteAutomaton SLUG_AUTOMATON = slugAutomaton();

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
        if (!SLUG_AUTOMATON.accepts(request.slug())) {
            throw new BadRequestException(
                "Slug inválido: solo minúsculas, dígitos y guiones simples entre segmentos");
        }
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

    // ---- Helpers privados a nivel de clase (NO dentro de createCategory) ----

    private static FiniteAutomaton slugAutomaton() {
        // 0=start, 1=segmento, 2=tras guión (exige un carácter alfanumérico después)
        Map<Integer, Map<Character, Integer>> t = new HashMap<>();
        Map<Character, Integer> d0 = new HashMap<>(); alnum(d0, 1);
        Map<Character, Integer> d1 = new HashMap<>(); alnum(d1, 1); d1.put('-', 2);
        Map<Character, Integer> d2 = new HashMap<>(); alnum(d2, 1);
        t.put(0, d0); t.put(1, d1); t.put(2, d2);
        return new FiniteAutomaton(0, Set.of(1), t);
    }

    private static void alnum(Map<Character, Integer> m, int target) {
        for (char c = 'a'; c <= 'z'; c++) m.put(c, target);
        for (char c = '0'; c <= '9'; c++) m.put(c, target);
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
