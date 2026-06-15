package com.nestorria.server.modules.agency;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nestorria.server.modules.agency.dto.AgencyRegistrationRequest;
import com.nestorria.server.modules.agency.dto.AgencyResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/agencies")
@Tag(name = "Agencies", description = "Gestión de agencias inmobiliarias")
public class AgencyController {

    private final AgencyService agencyService;

    public AgencyController(AgencyService agencyService) {
        this.agencyService = agencyService;
    }

    @PostMapping
    public ResponseEntity<AgencyResponse> register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AgencyRegistrationRequest request) {
        AgencyResponse response = agencyService.register(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public AgencyResponse getMyAgency(@AuthenticationPrincipal Jwt jwt) {
        return agencyService.getMyAgency(jwt.getSubject());
    }
}
