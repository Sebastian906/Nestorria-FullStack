package com.nestorria.server.modules.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.algorithm.DynamicProgrammingUtils;
import com.nestorria.server.common.algorithm.DynamicProgrammingUtils.AccumulationResult;
import com.nestorria.server.modules.booking.dto.DashboardMetricsResponse;
import com.nestorria.server.modules.booking.dto.MonthlyMetrics;
import com.nestorria.server.modules.booking.dto.PeriodComparison;

import lombok.RequiredArgsConstructor;

/**
 * Servicio de métricas para el dashboard del panel administrativo.
 * Implementa Prefix-Sum DP para:
 * 1. Revenue por mes: acumulación de ingresos en ventanas temporales
 * 2. Comparativas entre períodos: prefix-sum para O(1) queries de rango
 * 3. Ocupación: cálculo de noches reservadas vs disponibles
 * Complejidad:
 * - Preprocesamiento: O(n) donde n = total bookings
 * - Query de rango: O(1) usando prefix-sum
 * - Comparativa: O(1)
 */
@Service
@RequiredArgsConstructor
public class DashboardMetricsService {

    private final BookingRepository bookingRepository;

    /**
     * Obtiene métricas completas del dashboard para un período dado.
     * @param agencyId - ID de la agencia
     * @param startDate - fecha de inicio del período
     * @param endDate - fecha de fin del período
     * @return métricas acumuladas
     */
    @Transactional(readOnly = true)
    public DashboardMetricsResponse getAgencyMetrics(
            String agencyId, LocalDate startDate, LocalDate endDate) {
        
        ZoneId zone = ZoneId.systemDefault();
        Instant startInstant = startDate.atStartOfDay(zone).toInstant();
        // endDate +1 day, half-open range [start, end)
        Instant endInstant = endDate.plusDays(1).atStartOfDay(zone).toInstant();
        
        // Obtener todos los bookings del período
        List<Booking> bookings = bookingRepository.findByAgencyIdAndDateRange(
            agencyId, startInstant, endInstant);
        
        // DP: Acumulación en una sola pasada
        Map<YearMonth, MonthlyMetrics> metricsByMonth = accumulateMetrics(bookings);
        
        // DP: Prefix-Sum para queries de rango
        long[] revenuePrefix = buildRevenuePrefixSum(metricsByMonth);
        
        // Calcular métricas totales
        AccumulationResult totalRevenue = DynamicProgrammingUtils.accumulate(
            bookings.stream().map(Booking::getTotalPrice).toList());
        
        int totalNights = bookings.stream()
            .mapToInt(b -> (int) ChronoUnit.DAYS.between(b.getCheckInDate(), b.getCheckOutDate()))
            .sum();
        
        // Construir respuesta
        return new DashboardMetricsResponse(
            bookings.size(),
            (long) totalRevenue.sum(),
            totalNights,
            totalRevenue.average(),
            new ArrayList<>(metricsByMonth.values()),
            revenuePrefix
        );
    }

    /**
     * Compara dos períodos y retorna la diferencia.
     * Prefix-Sum DP: permite comparar cualquier rango en O(1).
     * @param agencyId - ID de la agencia
     * @param period1Start - inicio del primer período
     * @param period1End - fin del primer período
     * @param period2Start - inicio del segundo período
     * @param period2End - fin del segundo período
     * @return comparación entre períodos
     */
    @Transactional(readOnly = true)
    public PeriodComparison comparePeriods(
            String agencyId,
            LocalDate period1Start, LocalDate period1End,
            LocalDate period2Start, LocalDate period2End) {
        
        // Obtener métricas de ambos períodos
        DashboardMetricsResponse metrics1 = getAgencyMetrics(
            agencyId, period1Start, period1End);
        DashboardMetricsResponse metrics2 = getAgencyMetrics(
            agencyId, period2Start, period2End);
        
        // Calcular diferencias
        long revenueDiff = metrics2.totalRevenue() - metrics1.totalRevenue();
        int bookingsDiff = metrics2.totalBookings() - metrics1.totalBookings();
        int nightsDiff = metrics2.totalNights() - metrics1.totalNights();
        
        // Calcular porcentajes de cambio
        double revenueChangePercent = metrics1.totalRevenue() > 0
            ? (double) revenueDiff / metrics1.totalRevenue() * 100
            : 0.0;
        
        double bookingsChangePercent = metrics1.totalBookings() > 0
            ? (double) bookingsDiff / metrics1.totalBookings() * 100
            : 0.0;
        
        return new PeriodComparison(
            metrics1, metrics2,
            revenueDiff, bookingsDiff, nightsDiff,
            revenueChangePercent, bookingsChangePercent
        );
    }

    /**
     * Obtiene la comparativa del mes actual vs mes anterior.
     * @param agencyId - ID de la agencia
     * @return comparación mensual
     */
    @Transactional(readOnly = true)
    public PeriodComparison getCurrentMonthVsPrevious(String agencyId) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);
        
        return comparePeriods(
            agencyId,
            previousMonth.atDay(1), previousMonth.atEndOfMonth(),
            currentMonth.atDay(1), currentMonth.atEndOfMonth()
        );
    }

    /**
     * Obtiene la comparativa del año actual vs año anterior.
     * Compara año completo (enero 1 - hoy) vs año anterior completo.
     * @param agencyId - ID de la agencia
     * @return comparación anual
     */
    @Transactional(readOnly = true)
    public PeriodComparison getCurrentYearVsPrevious(String agencyId) {
        LocalDate today = LocalDate.now();
        LocalDate thisYearStart = today.withDayOfYear(1);
        LocalDate lastYearStart = thisYearStart.minusYears(1);
        LocalDate lastYearEnd = thisYearStart.minusDays(1);
        
        return comparePeriods(
            agencyId,
            lastYearStart, lastYearEnd,
            thisYearStart, today
        );
    }

    /**
     * DP: Acumula métricas por mes en una sola pasada.
     * Complejidad: O(n) donde n = total bookings
     * Espacio: O(m) donde m = meses únicos
     * @param bookings - lista de bookings
     * @return mapa ordenado de mes → métricas
     */
    private Map<YearMonth, MonthlyMetrics> accumulateMetrics(List<Booking> bookings) {
        Map<YearMonth, MonthlyMetrics> metrics = new TreeMap<>();
        
        for (Booking booking : bookings) {
            boolean sameMonth = booking.getCheckOutDate().getMonthValue() == booking.getCheckInDate().getMonthValue();
            
            if (sameMonth) {
                // Booking dentro de un solo mes: acumular directo
                YearMonth month = YearMonth.from(booking.getCheckInDate());
                MonthlyMetrics current = metrics.getOrDefault(month, MonthlyMetrics.empty(month));
                metrics.put(month, current.addBooking(booking));
            } else {
                // Booking cruza meses: distribuir proporcionalmente (incluso check-in month)
                distributeBookingAcrossMonths(booking, metrics);
            }
        }
        
        return metrics;
    }

    /**
     * Distribuye un booking que cruza múltiples meses.
     * DP: cada mes recibe una proporción del revenue proporcional a las noches
     */
    private void distributeBookingAcrossMonths(
            Booking booking, Map<YearMonth, MonthlyMetrics> metrics) {
        
        LocalDate current = booking.getCheckInDate();
        LocalDate end = booking.getCheckOutDate();
        long totalNights = ChronoUnit.DAYS.between(current, end);
        long totalPrice = booking.getTotalPrice();
        long dailyRate = totalNights > 0 ? totalPrice / totalNights : 0;
        
        while (current.isBefore(end)) {
            YearMonth month = YearMonth.from(current);
            LocalDate monthEnd = month.atEndOfMonth();
            
            // Noches en este mes
            LocalDate effectiveEnd = end.isBefore(monthEnd) ? end : monthEnd;
            long nightsInMonth = ChronoUnit.DAYS.between(current, effectiveEnd);
            
            // Revenue proporcional
            long revenueInMonth = nightsInMonth * dailyRate;
            
            // Acumular
            MonthlyMetrics currentMetrics = metrics.getOrDefault(month, MonthlyMetrics.empty(month));
            MonthlyMetrics newMetrics = new MonthlyMetrics(
                month,
                currentMetrics.totalBookings() + (current.equals(booking.getCheckInDate()) ? 1 : 0),
                currentMetrics.totalRevenue() + revenueInMonth,
                currentMetrics.totalNights() + (int) nightsInMonth,
                0.0 // se recalcula después
            );
            
            // Recalcular promedio
            MonthlyMetrics finalMetrics = new MonthlyMetrics(
                month,
                newMetrics.totalBookings(),
                newMetrics.totalRevenue(),
                newMetrics.totalNights(),
                newMetrics.totalBookings() > 0 
                    ? (double) newMetrics.totalRevenue() / newMetrics.totalBookings()
                    : 0.0
            );
            
            metrics.put(month, finalMetrics);
            
            // Avanzar al siguiente mes
            current = monthEnd.plusDays(1);
        }
    }

    /**
     * DP: Construye array de prefix-sum para revenue por mes.
     * Preprocesamiento: O(m) donde m = meses
     * Query de rango: O(1)
     * @param metricsByMonth - métricas agrupadas por mes
     * @return array de prefix-sum
     */
    private long[] buildRevenuePrefixSum(Map<YearMonth, MonthlyMetrics> metricsByMonth) {
        List<Long> revenues = metricsByMonth.values().stream()
            .map(MonthlyMetrics::totalRevenue)
            .toList();
        
        return DynamicProgrammingUtils.prefixSum(revenues.stream()
            .mapToLong(Long::longValue)
            .toArray());
    }
}
