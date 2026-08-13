package com.nestorria.server.modules.properties;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nestorria.server.modules.properties.dto.CategoryNode;
import com.nestorria.server.modules.properties.dto.CreateCategoryRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Property category management")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Get full category tree")
    public ResponseEntity<List<CategoryNode>> getTree() {
        return ResponseEntity.ok(categoryService.getTree());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category by ID with direct children")
    public ResponseEntity<CategoryNode> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    @GetMapping("/{id}/path")
    @Operation(summary = "Get path from root to category")
    public ResponseEntity<List<String>> getCategoryPath(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryPath(id));
    }

    @GetMapping("/{id}/descendants")
    @Operation(summary = "Get all descendants of a category")
    public ResponseEntity<List<CategoryNode>> getDescendants(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getDescendants(id));
    }

    @PostMapping
    @Operation(summary = "Create a new category")
    public ResponseEntity<CategoryNode> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(request));
    }
}
