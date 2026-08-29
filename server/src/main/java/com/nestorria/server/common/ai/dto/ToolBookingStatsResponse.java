package com.nestorria.server.common.ai.dto;

public record ToolBookingStatsResponse(
    long totalBookings,
    long confirmedBookings,
    long pendingBookings,
    long cancelledBookings,
    long totalRevenue
) {}
