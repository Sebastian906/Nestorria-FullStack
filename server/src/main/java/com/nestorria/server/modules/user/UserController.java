package com.nestorria.server.modules.user;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nestorria.server.modules.user.dto.AddRecentSearchRequest;
import com.nestorria.server.modules.user.dto.UserProfileResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserProfileResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        return userService.getProfile(jwt.getSubject());
    }

    @PostMapping("/me/recent-searches")
    public ResponseEntity<Void> addRecentSearchCity(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddRecentSearchRequest request) {
        userService.addRecentSearchCity(jwt.getSubject(), request);
        return ResponseEntity.noContent().build();
    }
}
