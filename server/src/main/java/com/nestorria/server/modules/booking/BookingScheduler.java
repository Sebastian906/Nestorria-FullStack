package com.nestorria.server.modules.booking;

import java.util.List;

import com.nestorria.server.common.algorithm.GreedyUtils;

/**
 * Selecciona el máximo número de bookings no solapados para una propiedad
 * usando greedy interval scheduling.
 * Algoritmo:
 * 1. Ordenar bookings por checkOutDate ascendente (earliest finishing first)
 * 2. Seleccionar el primer booking
 * 3. Para cada booking subsiguiente: si su checkInDate >= último checkOutDate seleccionado, seleccionarlo
 * ¿Es óptimo? Sí. El problema de interval scheduling (seleccionar máximo número de
 * intervalos no solapados) tiene la propiedad de elección greedy y greedy produce
 * la solución óptima.
 * Time:  O(n log n) — dominado por sorting
 * Space: O(n)
 */
public final class BookingScheduler {

    private BookingScheduler() {}

    /**
     * Selecciona el máximo número de bookings no solapados de una lista.
     * Útil para determinar cuántas reservas puede alojar una propiedad
     * en un período dado.
     * @param bookings — bookings a evaluar (deben ser para la misma propiedad)
     * @return lista de bookings no solapados seleccionados
     */
    public static List<Booking> selectNonOverlapping(List<Booking> bookings) {
        if (bookings == null || bookings.isEmpty()) {
            return List.of();
        }

        return GreedyUtils.intervalScheduling(
            bookings,
            Booking::getCheckInDate,
            Booking::getCheckOutDate
        );
    }
}
