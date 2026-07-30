package com.nestorria.server.modules.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.nestorria.server.common.event.NotificationEvent;
import com.nestorria.server.common.mail.EmailService;
import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.agency.AgencyRepository;
import com.nestorria.server.modules.booking.Booking;
import com.nestorria.server.modules.booking.BookingRepository;
import com.nestorria.server.modules.booking.BookingService;
import com.nestorria.server.modules.booking.dto.BookingResponse;
import com.nestorria.server.modules.booking.dto.CreateBookingRequest;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyRepository;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;
import com.nestorria.server.modules.user.User;
import com.nestorria.server.modules.user.UserRepository;

import java.time.LocalDate;

@ExtendWith(MockitoExtension.class)
class BookingServiceNotificationTest {

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

    @InjectMocks
    private BookingService bookingService;

    @Captor
    private ArgumentCaptor<NotificationEvent> eventCaptor;

    private User testUser;
    private Property testProperty;
    private Agency testAgency;

    @BeforeEach
    void setUp() {
        testUser = new User("user-123", "Test User", "test@example.com", "https://example.com/img.jpg");

        testAgency = new Agency(
            "Test Agency",
            "123 Main St",
            "555-0100",
            "agency@test.com",
            "Test City",
            testUser
        );
        testAgency.setId("agency-456");

        testProperty = new Property(
            testAgency,
            "Test Property",
            "A beautiful property",
            "Test City",
            "Test Country",
            "456 Oak Ave",
            100,
            com.nestorria.server.modules.properties.PropertyType.HOUSE,
            new PriceDetails(200, null),
            new com.nestorria.server.modules.properties.embeddable.FacilityDetails(3, 2, 1),
            List.of("Pool", "Garden"),
            null
        );
        testProperty.setId("property-789");
    }

    @Test
    void createBooking_PublishesNotificationEvent() {
        // Arrange
        LocalDate checkIn = LocalDate.now().plusDays(7);
        LocalDate checkOut = LocalDate.now().plusDays(10);
        CreateBookingRequest request = new CreateBookingRequest(
            "property-789",
            checkIn,
            checkOut,
            2
        );

        when(bookingRepository.findPropertyForUpdate("property-789"))
            .thenReturn(Optional.of(testProperty));
        when(bookingRepository.existsOverlappingBooking(
            eq("property-789"), eq(checkIn), eq(checkOut),
            any(com.nestorria.server.modules.booking.BookingStatus.class)
        )).thenReturn(false);
        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(UUID.randomUUID().toString());
            return booking;
        });

        // Act
        bookingService.createBooking("user-123", request);

        // Assert
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationEvent capturedEvent = eventCaptor.getValue();

        assertEquals("user-123", capturedEvent.userId());
        assertEquals(NotificationType.BOOKING_CONFIRMED, capturedEvent.type());
        assertEquals("Reserva confirmada", capturedEvent.title());
        assertTrue(capturedEvent.message().contains("456 Oak Ave"));
        assertEquals("booking", capturedEvent.referenceType());
        assertNotNull(capturedEvent.referenceId());
    }

    @Test
    void createBooking_PublishesEventAfterSavingBooking() {
        // Arrange
        LocalDate checkIn = LocalDate.now().plusDays(7);
        LocalDate checkOut = LocalDate.now().plusDays(10);
        CreateBookingRequest request = new CreateBookingRequest(
            "property-789",
            checkIn,
            checkOut,
            2
        );

        when(bookingRepository.findPropertyForUpdate("property-789"))
            .thenReturn(Optional.of(testProperty));
        when(bookingRepository.existsOverlappingBooking(
            eq("property-789"), eq(checkIn), eq(checkOut),
            any(com.nestorria.server.modules.booking.BookingStatus.class)
        )).thenReturn(false);
        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(UUID.randomUUID().toString());
            return booking;
        });

        // Act
        BookingResponse response = bookingService.createBooking("user-123", request);

        // Assert
        assertNotNull(response);
        verify(bookingRepository).save(any(Booking.class));
        verify(eventPublisher).publishEvent(any(NotificationEvent.class));
        verify(emailService).sendBookingConfirmation(any());
    }
}
