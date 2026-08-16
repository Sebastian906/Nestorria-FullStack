package com.nestorria.server.modules.booking;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.nestorria.server.common.mail.EmailService;
import com.nestorria.server.common.outbox.OutboxEventService;
import com.nestorria.server.modules.agency.AgencyRepository;
import com.nestorria.server.modules.booking.dto.MultiAvailabilityResponse;
import com.nestorria.server.modules.booking.dto.PropertyAvailabilityResult;
import com.nestorria.server.modules.payment.InvoiceRepository;
import com.nestorria.server.modules.payment.InvoiceService;
import com.nestorria.server.modules.payment.StripeClient;
import com.nestorria.server.modules.properties.PropertyRepository;
import com.nestorria.server.modules.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class BookingServiceMultiAvailabilityTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private AgencyRepository agencyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private StripeClient stripeClient;

    private BookingService bookingService;

    private static final LocalDate CHECK_IN = LocalDate.of(2026, 9, 1);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 9, 5);

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
            bookingRepository, propertyRepository, agencyRepository,
            outboxEventService, userRepository, emailService,
            eventPublisher, invoiceService, invoiceRepository, stripeClient);
    }

    @Test
    void allPropertiesAvailable_returnsAllAvailable() {
        when(propertyRepository.existsById("A")).thenReturn(true);
        when(propertyRepository.existsById("B")).thenReturn(true);
        when(bookingRepository.existsOverlappingBooking("A", CHECK_IN, CHECK_OUT, BookingStatus.CANCELLED))
            .thenReturn(false);
        when(bookingRepository.existsOverlappingBooking("B", CHECK_IN, CHECK_OUT, BookingStatus.CANCELLED))
            .thenReturn(false);

        MultiAvailabilityResponse response = bookingService.checkMultiPropertyAvailability(
            List.of("A", "B"), CHECK_IN, CHECK_OUT);

        assertTrue(response.allAvailable());
        assertEquals(2, response.results().size());
        assertTrue(response.results().get(0).available());
        assertTrue(response.results().get(1).available());
    }

    @Test
    void onePropertyUnavailable_reportsConflict() {
        when(propertyRepository.existsById("A")).thenReturn(true);
        when(propertyRepository.existsById("B")).thenReturn(true);
        when(bookingRepository.existsOverlappingBooking("A", CHECK_IN, CHECK_OUT, BookingStatus.CANCELLED))
            .thenReturn(false);
        when(bookingRepository.existsOverlappingBooking("B", CHECK_IN, CHECK_OUT, BookingStatus.CANCELLED))
            .thenReturn(true);

        MultiAvailabilityResponse response = bookingService.checkMultiPropertyAvailability(
            List.of("A", "B"), CHECK_IN, CHECK_OUT);

        assertFalse(response.allAvailable());
        assertEquals(2, response.results().size());
        assertTrue(response.results().get(0).available());
        assertFalse(response.results().get(1).available());
    }

    @Test
    void propertyNotFound_reportsError() {
        when(propertyRepository.existsById("A")).thenReturn(true);
        when(propertyRepository.existsById("MISSING")).thenReturn(false);
        when(bookingRepository.existsOverlappingBooking("A", CHECK_IN, CHECK_OUT, BookingStatus.CANCELLED))
            .thenReturn(false);

        MultiAvailabilityResponse response = bookingService.checkMultiPropertyAvailability(
            List.of("A", "MISSING"), CHECK_IN, CHECK_OUT);

        assertFalse(response.allAvailable());
        assertEquals(2, response.results().size());

        PropertyAvailabilityResult missingResult = response.results().stream()
            .filter(r -> r.propertyId().equals("MISSING")).findFirst().orElseThrow();
        assertFalse(missingResult.available());
        assertEquals("Propiedad no encontrada", missingResult.reason());
    }

    @Test
    void duplicatePropertyIds_areDeduplicated() {
        when(propertyRepository.existsById("A")).thenReturn(true);
        when(bookingRepository.existsOverlappingBooking("A", CHECK_IN, CHECK_OUT, BookingStatus.CANCELLED))
            .thenReturn(false);

        MultiAvailabilityResponse response = bookingService.checkMultiPropertyAvailability(
            List.of("A", "A", "A"), CHECK_IN, CHECK_OUT);

        // Dedup: only one result despite 3 identical IDs
        assertEquals(1, response.results().size());
        assertTrue(response.allAvailable());
        // Repository queried only once per unique ID
        verify(propertyRepository).existsById("A");
    }

    @Test
    void emptyPropertyIds_returnsEmptyResults() {
        MultiAvailabilityResponse response = bookingService.checkMultiPropertyAvailability(
            List.of(), CHECK_IN, CHECK_OUT);

        assertTrue(response.allAvailable());
        assertTrue(response.results().isEmpty());
    }

    @Test
    void singleProperty_returnsSingleResult() {
        when(propertyRepository.existsById("X")).thenReturn(true);
        when(bookingRepository.existsOverlappingBooking("X", CHECK_IN, CHECK_OUT, BookingStatus.CANCELLED))
            .thenReturn(true);

        MultiAvailabilityResponse response = bookingService.checkMultiPropertyAvailability(
            List.of("X"), CHECK_IN, CHECK_OUT);

        assertEquals(1, response.results().size());
        assertFalse(response.allAvailable());
        assertFalse(response.results().get(0).available());
    }

    @Test
    void invalidDateRange_throwsException() {
        LocalDate badCheckIn = LocalDate.of(2026, 9, 10);
        LocalDate badCheckOut = LocalDate.of(2026, 9, 5);

        org.junit.jupiter.api.Assertions.assertThrows(
            com.nestorria.server.common.exception.BadRequestException.class,
            () -> bookingService.checkMultiPropertyAvailability(
                List.of("A"), badCheckIn, badCheckOut));
    }

    @Test
    void allUnavailable_returnsAllUnavailable() {
        when(propertyRepository.existsById("A")).thenReturn(true);
        when(propertyRepository.existsById("B")).thenReturn(true);
        when(bookingRepository.existsOverlappingBooking("A", CHECK_IN, CHECK_OUT, BookingStatus.CANCELLED))
            .thenReturn(true);
        when(bookingRepository.existsOverlappingBooking("B", CHECK_IN, CHECK_OUT, BookingStatus.CANCELLED))
            .thenReturn(true);

        MultiAvailabilityResponse response = bookingService.checkMultiPropertyAvailability(
            List.of("A", "B"), CHECK_IN, CHECK_OUT);

        assertFalse(response.allAvailable());
        assertTrue(response.results().stream().noneMatch(PropertyAvailabilityResult::available));
    }

    @Test
    void noRepositoryCallsAfterDuplicateProcessing() {
        when(propertyRepository.existsById("A")).thenReturn(true);
        when(bookingRepository.existsOverlappingBooking("A", CHECK_IN, CHECK_OUT, BookingStatus.CANCELLED))
            .thenReturn(false);

        bookingService.checkMultiPropertyAvailability(
            List.of("A", "B", "A", "B"), CHECK_IN, CHECK_OUT);

        // A and B each queried once; duplicates skipped
        verify(propertyRepository).existsById("A");
        verify(propertyRepository).existsById("B");
        verify(bookingRepository).existsOverlappingBooking(
            eq("A"), any(), any(), any());
        verify(bookingRepository).existsOverlappingBooking(
            eq("B"), any(), any(), any());
    }
}
