package com.nestorria.server.modules.booking;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.exception.BadRequestException;
import com.nestorria.server.common.exception.ConflictException;
import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.agency.AgencyRepository;
import com.nestorria.server.modules.booking.dto.AgencyDashboardResponse;
import com.nestorria.server.modules.booking.dto.BookingResponse;
import com.nestorria.server.modules.booking.dto.CheckAvailabilityRequest;
import com.nestorria.server.modules.booking.dto.CreateBookingRequest;
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

    public BookingService(
            BookingRepository bookingRepository,
            PropertyRepository propertyRepository,
            AgencyRepository agencyRepository,
            UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.propertyRepository = propertyRepository;
        this.agencyRepository = agencyRepository;
        this.userRepository = userRepository;
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

        Property property = bookingRepository.findByIdWithLock(request.propertyId())
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
        long totalPrice = (long) (rentPrice * nights);

        Booking booking = new Booking(
            user,
            property,
            property.getAgency(),
            request.checkInDate(),
            request.checkOutDate(),
            totalPrice,
            request.guests()
        );

        return BookingResponse.fromEntity(bookingRepository.save(booking));
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
}
