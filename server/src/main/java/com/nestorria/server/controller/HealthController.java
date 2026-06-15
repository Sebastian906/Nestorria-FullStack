package com.nestorria.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Gestión de estado de la API")
public class HealthController {
    @GetMapping("/")
    public String health() {
        return "API successfully connected";
    }
}
