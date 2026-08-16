package com.nestorria.server.modules.booking;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.event.NotificationEvent;
import com.nestorria.server.common.exception.BadRequestException;
import com.nestorria.server.common.exception.ConflictException;
import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.common.mail.BookingEmailData;
import com.nestorria.server.common.mail.EmailService;
import com.nestorria.server.common.outbox.OutboxEventService;
import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.agency.AgencyRepository;
import com.nestorria.server.modules.booking.dto.AgencyDashboardResponse;
import com.nestorria.server.modules.booking.dto.BookingResponse;
import com.nestorria.server.modules.booking.dto.CheckAvailabilityRequest;
import com.nestorria.server.modules.booking.dto.CreateBookingRequest;
import com.nestorria.server.modules.booking.dto.MultiAvailabilityResponse;
import com.nestorria.server.modules.booking.dto.PropertyAvailabilityResult;
import com.nestorria.server.modules.notification.NotificationType;
import com.nestorria.server.modules.payment.Invoice;
import com.nestorria.server.modules.payment.InvoiceRepository;
import com.nestorria.server.modules.payment.InvoiceService;
import com.nestorria.server.modules.payment.InvoiceStatus;
import com.nestorria.server.modules.payment.StripeClient;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyRepository;
import com.nestorria.server.modules.user.User;
import com.nestorria.server.modules.user.UserRepository;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final AgencyRepository agencyRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventService outboxEventService;
    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final StripeClient stripeClient;

    public BookingService(
            BookingRepository bookingRepository,
            PropertyRepository propertyRepository,
            AgencyRepository agencyRepository,
            OutboxEventService outboxEventService,
            UserRepository userRepository,
            EmailService emailService,
            ApplicationEventPublisher eventPublisher,
            InvoiceService invoiceService,
            InvoiceRepository invoiceRepository,
            StripeClient stripeClient) {
        this.bookingRepository = bookingRepository;
        this.propertyRepository = propertyRepository;
        this.agencyRepository = agencyRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.eventPublisher = eventPublisher;
        this.outboxEventService = outboxEventService;
        this.invoiceService = invoiceService;
        this.invoiceRepository = invoiceRepository;
        this.stripeClient = stripeClient;
    }

    @Transactional(readOnly = true)
    public boolean checkAvailability(CheckAvailabilityRequest request) {
        validateDateRange(request.checkInDate(), request.checkOutDate());

        if (!propertyRepository.existsById(request.propertyId())) {
            throw new ResourceNotFoundException("Propiedad no encontrada: " + request.propertyId());
        }

        return isPropertyAvailable(request.propertyId(), request.checkInDate(), request.checkOutDate());
    }

    @Transactional
    public BookingResponse createBooking(String userId, CreateBookingRequest request) {
        validateDateRange(request.checkInDate(), request.checkOutDate());

        Property property = bookingRepository.findPropertyForUpdate(request.propertyId())
            .orElseThrow(() -> new ResourceNotFoundException("Propiedad no encontrada: " + request.propertyId()));

        if (!isPropertyAvailable(request.propertyId(), request.checkInDate(), request.checkOutDate())) {
            throw new ConflictException("La propiedad no está disponible en las fechas seleccionadas");
        }

        Integer rentPrice = property.getPrice().getRent();
        if (rentPrice == null) {
            throw new BadRequestException("La propiedad no tiene un precio de renta configurado");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userId));

        long nights = ChronoUnit.DAYS.between(request.checkInDate(), request.checkOutDate());
        if (nights <= 0) {
            throw new BadRequestException("La reserva debe ser de al menos una noche");
        }
        long totalPrice = (long) rentPrice * nights;

        Booking booking = new Booking(
        user, property, property.getAgency(),
        request.checkInDate(), request.checkOutDate(),
        totalPrice, request.guests()
        );

        booking.confirm();

        Booking savedBooking = bookingRepository.save(booking);

        invoiceService.createBookingInvoice(savedBooking);

        emailService.sendBookingConfirmation(new BookingEmailData(
            savedBooking.getId(),
            user.getEmail(),
            property.getAgency().getName(),
            property.getAddress(),
            request.checkInDate(),
            request.checkOutDate(),
            totalPrice,
            nights,
            request.guests()
        ));

        // Publicar evento de notificación via outbox (persist + retry)
        outboxEventService.publish(
            new NotificationEvent(
                userId,
                NotificationType.BOOKING_CONFIRMED,
                NotificationType.BOOKING_CONFIRMED.defaultTitle(),
                "Tu reserva en %s ha sido confirmada.".formatted(property.getAddress()),
                "booking",
                savedBooking.getId()
            ),
            "Booking",
            savedBooking.getId()
        );

        return BookingResponse.fromEntity(savedBooking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getUserBookings(String userId) {
        return bookingRepository.findByUserId(userId)
            .stream()
            .map(BookingResponse::fromEntity)
            .toList();
    }

    @Transactional(readOnly = true)
    public AgencyDashboardResponse getAgencyDashboard(String userId) {
        Agency agency = agencyRepository.findByOwnerId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("El usuario no tiene una agencia registrada"));

        List<Booking> bookings = bookingRepository.findByAgencyId(agency.getId());

        long totalRevenue = bookings.stream()
            .filter(Booking::isPaid)
            .mapToLong(Booking::getTotalPrice)
            .sum();

        List<BookingResponse> bookingResponses = bookings.stream()
            .map(BookingResponse::fromEntity)
            .toList();

        return new AgencyDashboardResponse(bookings.size(), totalRevenue, bookingResponses);
    }

    private boolean isPropertyAvailable(String propertyId, LocalDate checkInDate, LocalDate checkOutDate) {
        return !bookingRepository.existsOverlappingBooking(
            propertyId, checkInDate, checkOutDate, BookingStatus.CANCELLED
        );
    }

    private void validateDateRange(LocalDate checkInDate, LocalDate checkOutDate) {
        if (!checkOutDate.isAfter(checkInDate)) {
            throw new BadRequestException("La fecha de salida debe ser posterior a la fecha de entrada");
        }
    }

    @Transactional
    public Map<String, String> createStripeCheckoutSession(String bookingId, String userId, String origin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada: " + bookingId));

        String tenantId = booking.getUser().getId();
        String agencyOwnerId = booking.getAgency().getOwner().getId();
        if (!userId.equals(tenantId) && !userId.equals(agencyOwnerId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "No eres parte de esta reserva");
        }

        Invoice invoiceLookup = invoiceService.findByBookingId(bookingId);

        // Bloqueo pesimista: garantiza que solo una request puede crear sesión por factura
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceLookup.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Factura no encontrada: " + invoiceLookup.getId()));

        if (!InvoiceStatus.PAYABLE.contains(invoice.getStatus())) {
            throw new BadRequestException(
                    "La factura no se puede pagar. Estado actual: " + invoice.getStatus());
        }

        // Reutilizar sesión activa existente
        if (invoice.getStripeSessionId() != null) {
            var existingSession = stripeClient.retrieveCheckoutSession(invoice.getStripeSessionId());
            if (existingSession != null && "open".equals(existingSession.getStatus())) {
                return Map.of("url", existingSession.getUrl());
            }
            // Sesión expirada o completada → crear nueva
        }

        String propertyAddress = booking.getProperty().getAddress();
        String successUrl = origin + "/processing/my-bookings";
        String cancelUrl = origin + "/my-bookings";

        Map<String, String> metadata = new HashMap<>();
        metadata.put("bookingId", bookingId);
        metadata.put("invoiceId", invoice.getId());
        metadata.put("propertyName", propertyAddress);

        long amountInCents = invoice.getAmountDue();

        var session = stripeClient.createCheckoutSession(
                amountInCents,
                invoice.getCurrency(),
                metadata,
                successUrl,
                cancelUrl);

        invoice.setStripeSessionId(session.getId());
        invoiceRepository.save(invoice);

        return Map.of("url", session.getUrl());
    }

    /**
     * Verifica disponibilidad de múltiples propiedades para las mismas fechas.
     * Implementación: loop independiente por propiedad — O(n) donde n = número de propiedades.
     * ¿Por qué no backtracking? Las propiedades son independientes: reservar propiedad A
     * no afecta la disponibilidad de propiedad B. Cada verificación es una query independiente.
     * Backtracking solo se justifica cuando las decisiones son interdependientes.
     * Set<String> se usa para detectar duplicados en input.
     * Se retorna el primer conflicto por propiedad con razón específica.
     */
    @Transactional(readOnly = true)
    public MultiAvailabilityResponse checkMultiPropertyAvailability(
            List<String> propertyIds, LocalDate checkInDate, LocalDate checkOutDate) {

        validateDateRange(checkInDate, checkOutDate);

        // Set para detectar IDs duplicados en input — O(n) dedup
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        List<PropertyAvailabilityResult> results = new java.util.ArrayList<>();

        for (String propertyId : propertyIds) {
            // Dedup: si el ID ya fue procesado, saltar
            if (!seen.add(propertyId)) {
                continue;
            }

            // Verificar que la propiedad existe
            if (!propertyRepository.existsById(propertyId)) {
                results.add(PropertyAvailabilityResult.unavailable(
                    propertyId, "Propiedad no encontrada"));
                continue;
            }

            // Verificar disponibilidad — reutiliza lógica existente
            if (isPropertyAvailable(propertyId, checkInDate, checkOutDate)) {
                results.add(PropertyAvailabilityResult.available(propertyId));
            } else {
                results.add(PropertyAvailabilityResult.unavailable(
                    propertyId, "La propiedad no está disponible en las fechas seleccionadas"));
            }
        }

        boolean allAvailable = results.stream().allMatch(PropertyAvailabilityResult::available);
        return new MultiAvailabilityResponse(allAvailable, results);
    }
}
