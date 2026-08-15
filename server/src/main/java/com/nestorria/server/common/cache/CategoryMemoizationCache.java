package com.nestorria.server.common.cache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.nestorria.server.modules.properties.CategoryTree;

/**
 * Cache de memoización para el árbol de categorías.
 * Implementa Bottom-Up DP para construir el cache de descendientes:
 * 1. Carga todas las categorías una vez
 * 2. Construye el mapa de hijos
 * 3. Calcula descendientes de cada nodo (bottom-up, post-order)
 * 4. Cachea los resultados para queries futuras
 * Complejidad:
 * - Construcción: O(n) donde n = total categorías
 * - Query: O(1) amortizado después de la construcción
 * - Invalidación: O(1)
 * Patrón: Bottom-Up DP con memoización
 * - Los subproblemas son "descendientes de nodo X"
 * - Se resuelven de hojas a raíz (bottom-up)
 * - Los resultados se cachean para reutilización
 */
@Component
public class CategoryMemoizationCache {

    // Cache principal: categoryId → Set de IDs de descendientes
    private final ConcurrentHashMap<Long, Set<Long>> descendantCache = new ConcurrentHashMap<>();
    
    // Cache de paths: categoryId → Lista de nombres del path
    private final ConcurrentHashMap<Long, List<String>> pathCache = new ConcurrentHashMap<>();
    
    // Cache de árboles: parentId → Lista de hijos
    private final ConcurrentHashMap<Long, List<CategoryTree>> childrenCache = new ConcurrentHashMap<>();
    
    // Todas las categorías (para reconstrucción)
    private volatile List<CategoryTree> allCategories = new ArrayList<>();
    
    // Flag de invalidación
    private volatile boolean dirty = true;

    /**
     * Construye el cache bottom-up desde una lista de categorías.
     * Algoritmo Bottom-Up DP:
     * 1. Construir mapa de hijos (parentId → children)
     * 2. Para cada nodo hoja (sin hijos): descendantCache[id] = {id}
     * 3. Para cada nodo padre: descendantCache[id] = {id} ∪ ∪(descendantCache[child])
     * 4. Los padres se procesan después de los hijos (post-order)
     * @param categories - todas las categorías del sistema
     */
    public void buildCache(List<CategoryTree> categories) {
        if (categories == null || categories.isEmpty()) {
            clear();
            return;
        }
        
        this.allCategories = new ArrayList<>(categories);
        
        // Paso 1: Construir mapa de hijos
        ConcurrentHashMap<Long, List<CategoryTree>> tempChildren = new ConcurrentHashMap<>();
        for (CategoryTree cat : categories) {
            if (cat.getParent() != null) {
                tempChildren.computeIfAbsent(cat.getParent().getId(), k -> new ArrayList<>())
                    .add(cat);
            }
        }
        this.childrenCache.putAll(tempChildren);
        
        // Paso 2: Construir cache de descendientes bottom-up
        ConcurrentHashMap<Long, Set<Long>> tempDescendants = new ConcurrentHashMap<>();
        
        // Orden post-order: procesar hojas primero, luego padres
        List<CategoryTree> sorted = sortByDepthDesc(categories);
        
        for (CategoryTree cat : sorted) {
            Set<Long> descendants = new HashSet<>();
            descendants.add(cat.getId()); // Incluir a sí mismo
            
            // Agregar descendientes de todos los hijos
            List<CategoryTree> children = tempChildren.getOrDefault(cat.getId(), List.of());
            for (CategoryTree child : children) {
                descendants.addAll(tempDescendants.getOrDefault(child.getId(), Set.of()));
            }
            
            tempDescendants.put(cat.getId(), descendants);
        }
        
        this.descendantCache.putAll(tempDescendants);
        
        // Paso 3: Construir cache de paths (top-down)
        ConcurrentHashMap<Long, List<String>> tempPaths = new ConcurrentHashMap<>();
        for (CategoryTree cat : categories) {
            if (cat.getParent() == null) {
                // Nodo raíz
                tempPaths.put(cat.getId(), List.of(cat.getName()));
            } else {
                // Nodo hijo: path = parentPath + name
                List<String> parentPath = tempPaths.get(cat.getParent().getId());
                if (parentPath != null) {
                    List<String> path = new ArrayList<>(parentPath);
                    path.add(cat.getName());
                    tempPaths.put(cat.getId(), path);
                }
            }
        }
        this.pathCache.putAll(tempPaths);
        
        this.dirty = false;
    }

    /**
     * Obtiene todos los IDs de descendientes de una categoría (incluyendo ella misma).
     * Bottom-Up DP: el resultado ya está precalculado en el cache.
     * Complejidad: O(1)
     * @param categoryId - ID de la categoría
     * @return Set de IDs de descendientes, o null si no está en el cache
     */
    public Set<Long> getDescendantIds(Long categoryId) {
        Set<Long> ids = descendantCache.get(categoryId);
        return ids != null ? Set.copyOf(ids) : null;
    }

    /**
     * Obtiene el path de nombres de una categoría (de raíz a hoja).
     * Top-Down DP: el resultado ya está precalculado en el cache.
     * Complejidad: O(1)
     * @param categoryId - ID de la categoría
     * @return Lista de nombres del path, o null si no está en el cache
     */
    public List<String> getCategoryPath(Long categoryId) {
        List<String> path = pathCache.get(categoryId);
        return path != null ? List.copyOf(path) : null;
    }

    /**
     * Obtiene los hijos directos de una categoría.
     * Complejidad: O(1)
     */
    public List<CategoryTree> getChildren(Long parentId) {
        return childrenCache.getOrDefault(parentId, List.of());
    }

    /**
     * Invalida el cache (llamar después de CRUD de categorías).
     * Complejidad: O(1)
     */
    public void invalidate() {
        descendantCache.clear();
        pathCache.clear();
        childrenCache.clear();
        dirty = true;
    }

    // Limpia completamente el cache.
    public void clear() {
        invalidate();
        allCategories = new ArrayList<>();
    }

    // Verifica si el cache está sucio y necesita reconstrucción.
    public boolean isDirty() {
        return dirty;
    }

    // Obtiene el número de categorías cacheadas.
    public int size() {
        return descendantCache.size();
    }

    /**
     * Ordena categorías por profundidad descendente (hojas primero).
     * Necesario para procesamiento bottom-up.
     */
    private List<CategoryTree> sortByDepthDesc(List<CategoryTree> categories) {
        return categories.stream()
            .sorted((a, b) -> Integer.compare(getDepth(b), getDepth(a)))
            .toList();
    }

    // Calcula la profundidad de una categoría (número de padres).
    private int getDepth(CategoryTree category) {
        int depth = 0;
        CategoryTree current = category;
        while (current.getParent() != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }
}
