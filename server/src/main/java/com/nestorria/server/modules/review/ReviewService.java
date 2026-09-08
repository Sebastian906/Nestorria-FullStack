package com.nestorria.server.modules.review;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.ai.AiServiceClient;
import com.nestorria.server.common.exception.ConflictException;
import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyRepository;
import com.nestorria.server.modules.review.dto.CreateReviewRequest;
import com.nestorria.server.modules.review.dto.ReviewResponse;
import com.nestorria.server.modules.review.dto.UpdateReviewRequest;
import com.nestorria.server.modules.user.User;
import com.nestorria.server.modules.user.UserRepository;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final AiServiceClient aiServiceClient;
    private final Cache translateCache;
    private final Executor notificationTaskExecutor;

    public ReviewService(
            ReviewRepository reviewRepository,
            UserRepository userRepository,
            PropertyRepository propertyRepository,
            AiServiceClient aiServiceClient,
            CacheManager cacheManager,
            @Qualifier("notificationTaskExecutor") java.util.concurrent.Executor notificationTaskExecutor) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.propertyRepository = propertyRepository;
        this.aiServiceClient = aiServiceClient;
        this.translateCache = cacheManager.getCache("reviewTranslations");
        this.notificationTaskExecutor = notificationTaskExecutor;
    }

    @CacheEvict(cacheNames = {"ratingAggregates", "propertyListings", "ownerProperties"}, allEntries = true)
    @Transactional
    public ReviewResponse createReview(String userId, String propertyId, CreateReviewRequest request) {
        if (reviewRepository.existsByUserIdAndPropertyId(userId, propertyId)) {
            throw new ConflictException("review.already-exists");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("not-found"));

        Property property = propertyRepository.findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("not-found"));

        Review review = new Review(user, property, request.rating(), request.comment());

        String uiLocale = request.uiLocale() != null
            ? request.uiLocale()
            : LocaleContextHolder.getLocale().getLanguage();
        review.setOriginalLang(normalize(uiLocale));

        return ReviewResponse.fromEntity(reviewRepository.save(review));
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getPropertyReviews(String propertyId) {
        String target = target();
        List<Review> reviews = findPropertyReviews(propertyId);
        return reviews.stream().map(r -> ReviewResponse.of(r, display(r, target))).toList();
    }

    @Transactional(readOnly = true)
    protected List<Review> findPropertyReviews(String propertyId) {
        return reviewRepository.findByPropertyIdOrderByCreatedAtDesc(propertyId);
    }

    private String display(Review review, String target) {
        if (review.getComment() == null || review.getComment().isBlank()) {
            return review.getComment();
        }
        String orig = review.getOriginalLang() != null ? review.getOriginalLang() : "es";
        if (target.equals(orig)) {
            return review.getComment();
        }
        String key = review.getId() + ":" + target;
        try {
            if (translateCache != null) {
                String cached = translateCache.get(key, String.class);
                if (cached != null) {
                    return cached;
                }
            }
            String translated = aiServiceClient.translate(review.getComment(), orig, target);
            if (translated == null || translated.isBlank()) {
                return review.getComment();
            }
            if (translateCache != null) {
                translateCache.put(key, translated);
            }
            return translated;
        } catch (Exception e) {
            return review.getComment();
        }
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getUserReviews(String userId) {
        String target = target();
        List<Review> reviews = findUserReviews(userId);
        return reviews.stream().map(r -> ReviewResponse.of(r, display(r, target))).toList();
    }

    @Transactional(readOnly = true)
    protected List<Review> findUserReviews(String userId) {
        return reviewRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private String target() {
        String lang = LocaleContextHolder.getLocale().getLanguage();
        return normalize(lang);
    }

    private String normalize(String lang) {
        if (lang != null && lang.toLowerCase().startsWith("es")) {
            return "es";
        }
        return "en";
    }

    @CacheEvict(cacheNames = {"ratingAggregates", "propertyListings", "ownerProperties"}, allEntries = true)
    @Transactional
    public ReviewResponse updateReview(String reviewId, String userId, UpdateReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new ResourceNotFoundException("Reseña no encontrada: " + reviewId));

        if (!review.getUser().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "No tienes permiso para modificar esta reseña"
            );
        }

        if (request.rating() != null) {
            review.setRating(request.rating());
        }
        if (request.comment() != null) {
            review.setComment(request.comment());
        }

        return ReviewResponse.fromEntity(reviewRepository.save(review));
    }

    @CacheEvict(cacheNames = {"ratingAggregates", "propertyListings", "ownerProperties"}, allEntries = true)
    @Transactional
    public void deleteReview(String reviewId, String userId) {
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new ResourceNotFoundException("Reseña no encontrada: " + reviewId));

        if (!review.getUser().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "No tienes permiso para eliminar esta reseña"
            );
        }

        reviewRepository.delete(review);
    }

    /**
     * Obtiene ratings promedio para múltiples propiedades.
     * Divide-and-conquer: cuando hay muchos propertyIds (>100),
     * se dividen en chunks y se procesan en paralelo.
     * Cada chunk ejecuta su propia query SQL y construye su Map.
     * Los resultados se combinan al final.
     * Para listas pequeñas (<100), usa el enfoque secuencial directo.
     * Complejidad: O(n) para el loop de construcción del Map
     * con divide-and-conquer: O(n/p) donde p = número de chunks paralelos.
     */
    @Cacheable(cacheNames = "ratingAggregates")
    @Transactional(readOnly = true)
    public Map<String, RatingAggregate> getAverageRatings(List<String> propertyIds) {
        if (propertyIds == null || propertyIds.isEmpty()) {
            return Map.of();
        }

        // Para listas pequeñas, usar enfoque secuencial (evitar overhead de paralelismo)
        if (propertyIds.size() <= 100) {
            return getAverageRatingsSequential(propertyIds);
        }

        // Divide-and-conquer: procesar chunks en paralelo
        return getAverageRatingsParallel(propertyIds);
    }

    /**
     * Versión secuencial para listas pequeñas.
     * O(n) donde n = número de propertyIds.
     */
    private Map<String, RatingAggregate> getAverageRatingsSequential(List<String> propertyIds) {
        List<Object[]> aggregates = reviewRepository.findRatingAggregatesByPropertyIds(propertyIds);

        Map<String, RatingAggregate> result = new HashMap<>();
        for (Object[] row : aggregates) {
            String propertyId = (String) row[0];
            Double avgRating = (Double) row[1];
            Long count = (Long) row[2];
            result.put(propertyId, new RatingAggregate(avgRating, count.intValue()));
        }
        return result;
    }

    /**
     * Versión paralela para listas grandes.
     * Divide-and-conquer: divide propertyIds en chunks, procesa cada chunk
     * en un thread separado, y combina los resultados.
     * Complejidad: O(n/p) donde p = número de chunks.
     * En la práctica: O(n) pero con mejor latencia porque los chunks se procesan en paralelo.
     */
    private Map<String, RatingAggregate> getAverageRatingsParallel(List<String> propertyIds) {
        int chunkSize = 100; // Procesar 100 propertyIds por chunk

        // Dividir en chunks
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < propertyIds.size(); i += chunkSize) {
            chunks.add(propertyIds.subList(i, Math.min(i + chunkSize, propertyIds.size())));
        }

        // Procesar chunks en paralelo (divide)
        List<CompletableFuture<Map<String, RatingAggregate>>> futures = chunks.stream()
            .map(chunk -> CompletableFuture.supplyAsync(
                () -> getAverageRatingsSequential(chunk), notificationTaskExecutor))
            .toList();

        // Combinar resultados (conquer)
        Map<String, RatingAggregate> result = new HashMap<>();
        for (CompletableFuture<Map<String, RatingAggregate>> future : futures) {
            result.putAll(future.join());
        }

        return result;
    }

    public record RatingAggregate(Double averageRating, int reviewCount) {}
}
