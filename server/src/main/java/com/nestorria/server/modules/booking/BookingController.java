package com.nestorria.server.modules.booking;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nestorria.server.common.config.AppProperties;
import com.nestorria.server.modules.booking.BookingSortUtils.SortDirection;
import com.nestorria.server.modules.booking.BookingSortUtils.SortField;
import com.nestorria.server.modules.booking.dto.AgencyDashboardResponse;
import com.nestorria.server.modules.booking.dto.BookingResponse;
import com.nestorria.server.modules.booking.dto.CheckAvailabilityRequest;
import com.nestorria.server.modules.booking.dto.CheckAvailabilityResponse;
import com.nestorria.server.modules.booking.dto.CreateBookingRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Bookings", description = "Gestión de reservas")
public class BookingController {

    private final BookingService bookingService;
    private final AppProperties appProperties;

    public BookingController(BookingService bookingService, AppProperties appProperties) {
        this.bookingService = bookingService;
        this.appProperties = appProperties;
        appProperties.stripe().validate();
    }

    @Operation(summary = "Verificar disponibilidad de una propiedad")
    @PostMapping("/check-availability")
    public CheckAvailabilityResponse checkAvailability(
            @Valid @RequestBody CheckAvailabilityRequest request) {
        boolean isAvailable = bookingService.checkAvailability(request);
        return new CheckAvailabilityResponse(isAvailable);
    }

    @Operation(summary = "Crear una nueva reserva")
    @PostMapping
    public BookingResponse createBooking(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateBookingRequest request) {
        return bookingService.createBooking(jwt.getSubject(), request);
    }

    @Operation(summary = "Obtener las reservas del usuario")
    @GetMapping("/me")
    public List<BookingResponse> getMyBookings(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Campo de ordenamiento: CHECK_IN, CHECK_OUT, TOTAL_PRICE, STATUS, CREATED_AT")
            @RequestParam(required = false) SortField sortBy,
            @Parameter(description = "Dirección del ordenamiento: ASC o DESC")
            @RequestParam(required = false) SortDirection direction
    ) {
        List<BookingResponse> bookings = bookingService.getUserBookings(jwt.getSubject());

        if (sortBy != null) {
            SortDirection dir = direction != null ? direction : SortDirection.ASC;
            bookings = bookings.stream()
                .sorted(BookingSortUtils.getComparator(sortBy, dir))
                .toList();
        }

        return bookings;
    }

    @Operation(summary = "Obtener el dashboard de la agencia")
    @GetMapping("/agency")
    public AgencyDashboardResponse getAgencyDashboard(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.getAgencyDashboard(jwt.getSubject());
    }

    @Operation(summary = "Crear sesión de pago Stripe para una reserva")
    @PostMapping("/stripe")
    public ResponseEntity<Map<String, Object>> createStripePayment(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        String origin = resolveStripeOrigin(httpRequest);

        Map<String, String> result = bookingService.createStripeCheckoutSession(
            request.get("bookingId"), jwt.getSubject(), origin);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "url", result.get("url")
        ));
    }

    private String resolveStripeOrigin(HttpServletRequest request) {
        List<String> allowedOrigins = appProperties.stripe().originsAsList();

        String origin = request.getHeader("Origin");
        if (origin != null && allowedOrigins.contains(origin)) {
            return origin;
        }

        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                String refererOrigin = uri.getScheme() + "://" + uri.getAuthority();
                if (allowedOrigins.contains(refererOrigin)) {
                    return refererOrigin;
                }
            } catch (IllegalArgumentException e) {
                // Invalid Referer URI — ignore
            }
        }

        return allowedOrigins.isEmpty()
            ? throwNoOriginsConfigured()
            : allowedOrigins.get(0);
    }

    private String throwNoOriginsConfigured() {
        throw new IllegalStateException(
            "app.stripe.allowed-origins está vacío. "
            + "No se puede determinar la URL de redirección de Stripe.");
    }
}
