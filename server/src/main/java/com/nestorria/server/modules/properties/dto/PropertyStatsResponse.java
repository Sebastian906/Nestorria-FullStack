package com.nestorria.server.modules.properties.dto;

import java.util.Map;
import java.util.OptionalDouble;

import com.nestorria.server.common.algorithm.DivideAndConquerUtils;

public record PropertyStatsResponse(
    long totalProperties,
    Map<String, Long> byType,
    Map<String, Long> byCity,
    PriceStatistics priceStatistics
) {

    /**
     * Estadísticas de precio calculadas usando divide-and-conquer.
     * La mediana se calcula via quickselect O(n) average.
     */
    public record PriceStatistics(
        Integer min,
        Integer max,
        double average,
        OptionalDouble median,
        int count
    ) {
        /**
         * Calcula estadísticas de precio a partir de una lista de precios.
         * Divide-and-conquer: la mediana usa quickselect O(n) en lugar de sort O(n log n).
         */
        public static PriceStatistics fromPrices(java.util.List<Integer> prices) {
            java.util.List<Integer> valid = prices.stream()
                .filter(java.util.Objects::nonNull)
                .toList();

            if (valid.isEmpty()) {
                return new PriceStatistics(null, null, 0.0, OptionalDouble.empty(), 0);
            }

            int min = valid.stream().mapToInt(Integer::intValue).min().orElse(0);
            int max = valid.stream().mapToInt(Integer::intValue).max().orElse(0);
            double avg = valid.stream().mapToInt(Integer::intValue).average().orElse(0.0);

            // Divide-and-conquer: median via quickselect O(n) average
            OptionalDouble median = DivideAndConquerUtils.median(valid);

            return new PriceStatistics(min, max, avg, median, valid.size());
        }
    }
}
