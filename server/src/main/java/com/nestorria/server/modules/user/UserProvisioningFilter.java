package com.nestorria.server.modules.user;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class UserProvisioningFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public UserProvisioningFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String userId = jwt.getSubject();

            // At the top of the file, add the import:
            // import org.springframework.dao.DataIntegrityViolationException;
            
            userRepository.findById(userId).ifPresentOrElse(
                existing -> syncIfChanged(existing, jwt),
                () -> {
                    try {
                        createFromClaims(userId, jwt);
                    } catch (DataIntegrityViolationException e) {
                        // Lost race - another request created the user; sync instead
                        userRepository.findById(userId).ifPresent(user -> syncIfChanged(user, jwt));
                    }
                }
            );
        }

        filterChain.doFilter(request, response);
    }

    private void createFromClaims(String userId, Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        String image = jwt.getClaimAsString("image_url");

        User user = new User(userId, name, email, image);
        userRepository.save(user);
    }

    private void syncIfChanged(User user, Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        String image = jwt.getClaimAsString("image_url");

        boolean changed = false;
        if (email != null && !email.equals(user.getEmail())) { user.setEmail(email); changed = true; }
        if (name != null && !name.equals(user.getUsername())) { user.setUsername(name); changed = true; }
        if (image != null && !image.equals(user.getImage())) { user.setImage(image); changed = true; }

        if (changed) {
            userRepository.save(user);
        }
    }
}