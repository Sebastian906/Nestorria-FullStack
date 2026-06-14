package com.nestorria.server.modules.user;

import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.user.dto.AddRecentSearchRequest;
import com.nestorria.server.modules.user.dto.UserProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserService {

    private static final int MAX_RECENT_CITIES = 3;

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String userId) {
        User user = findUserOrThrow(userId);
        return new UserProfileResponse(user.getRole(), user.getRecentSearchedCities());
    }

    @Transactional
    public void addRecentSearchCity(String userId, AddRecentSearchRequest request) {
        User user = findUserOrThrow(userId);

        List<String> cities = new ArrayList<>(user.getRecentSearchedCities());

        if (cities.size() >= MAX_RECENT_CITIES) {
            cities.remove(0);
        }
        cities.add(request.city());

        user.setRecentSearchedCities(cities);
        userRepository.save(user);
    }

    private User findUserOrThrow(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userId));
    }
}
