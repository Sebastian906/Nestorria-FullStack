package com.nestorria.server.modules.booking;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nestorria.server.modules.booking.dto.AgencyDashboardResponse;
import com.nestorria.server.modules.booking.dto.BookingResponse;
import com.nestorria.server.modules.booking.dto.CheckAvailabilityRequest;
import com.nestorria.server.modules.booking.dto.CheckAvailabilityResponse;
import com.nestorria.server.modules.booking.dto.CreateBookingRequest;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Bookings", description = "Gestión de reservas")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/check-availability")
    public CheckAvailabilityResponse checkAvailability(@Valid @RequestBody CheckAvailabilityRequest request) {
        return new CheckAvailabilityResponse(bookingService.checkAvailability(request));
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateBookingRequest request) {
        BookingResponse response = bookingService.createBooking(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public List<BookingResponse> getMyBookings(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.getUserBookings(jwt.getSubject());
    }

    @GetMapping("/agency")
    public AgencyDashboardResponse getAgencyDashboard(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.getAgencyDashboard(jwt.getSubject());
    }
}
