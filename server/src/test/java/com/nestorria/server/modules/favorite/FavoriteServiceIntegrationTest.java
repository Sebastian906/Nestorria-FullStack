package com.nestorria.server.modules.favorite;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.agency.AgencyRepository;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyRepository;
import com.nestorria.server.modules.properties.PropertyType;
import com.nestorria.server.modules.properties.embeddable.FacilityDetails;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;
import com.nestorria.server.modules.properties.embeddable.PropertyLocation;
import com.nestorria.server.modules.user.User;
import com.nestorria.server.modules.user.UserRepository;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class FavoriteServiceIntegrationTest {

    @Autowired private FavoriteService favoriteService;
    @Autowired private FavoriteRepository favoriteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private AgencyRepository agencyRepository;

    @AfterEach
    void cleanup() {
        favoriteRepository.deleteAllInBatch();
        propertyRepository.deleteAllInBatch();
        agencyRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private User persistUser(String id) {
        return userRepository.save(new User(id, "User " + id, id + "@example.com", "img"));
    }

    private Property persistProperty(User owner) {
        Agency agency = agencyRepository.save(
            new Agency("Agency", "Addr", "Contact", "agency@example.com", "City", owner));
        return propertyRepository.save(new Property(agency, "Title", "Desc", "City", "Country",
            "Addr", 100, PropertyType.APARTMENT, new PriceDetails(), new FacilityDetails(),
            List.of(), new PropertyLocation(null, null, null, null)));
    }

    @Test
    void toggleTwiceSequentially_endsUnfavorited() {
        User owner = persistUser("owner");
        User user = persistUser("user");
        Property property = persistProperty(owner);

        assertThat(favoriteService.toggleFavorite(user.getId(), property.getId())).isTrue();
        assertThat(favoriteService.toggleFavorite(user.getId(), property.getId())).isFalse();
        assertThat(favoriteRepository.count()).isZero();
    }

    @Test
    void concurrentToggles_netZeroWithOneWinner() throws Exception {
        User owner = persistUser("owner");
        User user = persistUser("user");
        Property property = persistProperty(owner);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<Boolean> f1 = pool.submit(() -> {
                start.await();
                return favoriteService.toggleFavorite(user.getId(), property.getId());
            });
            Future<Boolean> f2 = pool.submit(() -> {
                start.await();
                return favoriteService.toggleFavorite(user.getId(), property.getId());
            });
            start.countDown();

            boolean r1 = f1.get(10, TimeUnit.SECONDS);
            boolean r2 = f2.get(10, TimeUnit.SECONDS);

            // Linearización correcta del toggle concurrente: uno inserta (true),
            // el otro pierde la carrera y completa el toggle (false) → net-zero.
            assertThat(r1 ^ r2).isTrue();
            assertThat(favoriteRepository.count()).isZero();
        } finally {
            pool.shutdownNow();
        }
    }
}
