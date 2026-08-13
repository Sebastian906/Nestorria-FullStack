package com.nestorria.server.modules.properties;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.properties.dto.CategoryNode;
import com.nestorria.server.modules.properties.dto.CreateCategoryRequest;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void getTree_emptyDatabase_returnsEmptyList() {
        given(categoryRepository.findAll()).willReturn(List.of());

        List<CategoryNode> tree = categoryService.getTree();

        assertThat(tree).isEmpty();
    }

    @Test
    void getTree_singleRoot_returnsSingleNode() {
        CategoryTree root = createCategory(1L, "Residential", "residential", null, 0);
        given(categoryRepository.findAll()).willReturn(List.of(root));

        List<CategoryNode> tree = categoryService.getTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).name()).isEqualTo("Residential");
        assertThat(tree.get(0).children()).isEmpty();
    }

    @Test
    void getTree_rootWithChildren_returnsNestedTree() {
        CategoryTree root = createCategory(1L, "Residential", "residential", null, 0);
        CategoryTree child1 = createCategory(2L, "House", "house", root, 1);
        CategoryTree child2 = createCategory(3L, "Apartment", "apartment", root, 1);
        root.setChildren(List.of(child1, child2));

        given(categoryRepository.findAll()).willReturn(List.of(root, child1, child2));

        List<CategoryNode> tree = categoryService.getTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).children()).hasSize(2);
    }

    @Test
    void getTree_multipleRoots_returnsForest() {
        CategoryTree root1 = createCategory(1L, "Residential", "residential", null, 0);
        CategoryTree root2 = createCategory(2L, "Commercial", "commercial", null, 0);

        given(categoryRepository.findAll()).willReturn(List.of(root1, root2));

        List<CategoryNode> tree = categoryService.getTree();

        assertThat(tree).hasSize(2);
    }

    @Test
    void getDescendants_withChildren_returnsAllDescendants() {
        CategoryTree root = createCategory(1L, "Residential", "residential", null, 0);
        CategoryTree child = createCategory(2L, "House", "house", root, 1);
        CategoryTree grandchild = createCategory(3L, "Single Family", "single-family", child, 2);

        given(categoryRepository.findById(1L)).willReturn(Optional.of(root));
        given(categoryRepository.findAll()).willReturn(List.of(root, child, grandchild));

        List<CategoryNode> descendants = categoryService.getDescendants(1L);

        assertThat(descendants).hasSize(1);
        assertThat(descendants.get(0).name()).isEqualTo("House");
        assertThat(descendants.get(0).children()).hasSize(1);
    }

    @Test
    void getDescendants_nonExistent_throwsException() {
        given(categoryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getDescendants(999L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCategoryPath_leaf_returnsFullPath() {
        CategoryTree root = createCategory(1L, "Colombia", "colombia", null, 0);
        CategoryTree city = createCategory(2L, "Bogota", "bogota", root, 1);
        CategoryTree neighborhood = createCategory(3L, "Chapinero", "chapinero", city, 2);

        given(categoryRepository.findById(3L)).willReturn(Optional.of(neighborhood));

        List<String> path = categoryService.getCategoryPath(3L);

        assertThat(path).containsExactly("Colombia", "Bogota", "Chapinero");
    }

    @Test
    void getCategoryPath_root_returnsSingleElement() {
        CategoryTree root = createCategory(1L, "Residential", "residential", null, 0);

        given(categoryRepository.findById(1L)).willReturn(Optional.of(root));

        List<String> path = categoryService.getCategoryPath(1L);

        assertThat(path).containsExactly("Residential");
    }

    @Test
    void createCategory_withoutParent_createsRoot() {
        CreateCategoryRequest request = new CreateCategoryRequest("Residential", "residential", null, null);
        CategoryTree saved = createCategory(1L, "Residential", "residential", null, 0);

        given(categoryRepository.save(any())).willReturn(saved);

        CategoryNode result = categoryService.createCategory(request);

        assertThat(result.name()).isEqualTo("Residential");
        assertThat(result.level()).isZero();
    }

    @Test
    void createCategory_withParent_createsChild() {
        CategoryTree parent = createCategory(1L, "Residential", "residential", null, 0);
        CreateCategoryRequest request = new CreateCategoryRequest("House", "house", null, 1L);
        CategoryTree saved = createCategory(2L, "House", "house", parent, 1);

        given(categoryRepository.findById(1L)).willReturn(Optional.of(parent));
        given(categoryRepository.save(any())).willReturn(saved);

        CategoryNode result = categoryService.createCategory(request);

        assertThat(result.level()).isEqualTo(1);
    }

    private CategoryTree createCategory(Long id, String name, String slug, CategoryTree parent, int level) {
        CategoryTree cat = new CategoryTree();
        cat.setId(id);
        cat.setName(name);
        cat.setSlug(slug);
        cat.setParent(parent);
        cat.setLevel(level);
        cat.setActive(true);
        return cat;
    }
}
