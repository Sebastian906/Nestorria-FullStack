package com.nestorria.server.common.algorithm;

import java.time.LocalDate;
import java.util.List;

import com.nestorria.server.modules.booking.Booking;
import com.nestorria.server.modules.booking.BookingStatus;

/**
 * Comparación documental entre brute force en memoria y acceso indexado en BD
 * para la detección de solapamientos de reservas (existsOverlappingBooking).
 * Brute force (este método): O(n) en memoria — recorre todos los bookings
 *   de la propiedad. Solo se justifica si los bookings ya están cargados.
 * Versión BD (BookingRepository.existsOverlappingBooking): el predicado
 *   property_id = ?  AND check_in_date < ? AND check_out_date > ?
 *   se resuelve con range scan sobre el índice (property_id, check_in_date).
 *   IMPORTANTE: NO es O(log n) garantizable — el predicado check_out > X
 *   queda como filtro residual; el costo típico es sub-lineal en bookings
 *   de una propiedad (después del filtro de property_id), pero el plan real
 *   debe verificarse con EXPLAIN ANALYZE.
 */
public final class BruteForceComparison {

    private BruteForceComparison() {}

    public static boolean existsOverlapBruteForce(
            List<Booking> bookings, LocalDate checkIn, LocalDate checkOut) {
        return bookings.stream()
            .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
            .anyMatch(b -> b.getCheckInDate().isBefore(checkOut)
                        && b.getCheckOutDate().isAfter(checkIn));
    }
}
