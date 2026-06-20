package com.nestorria.server.modules.booking.dto;

import java.util.List;

public record AgencyDashboardResponse(
    int totalBookings,
    long totalRevenue,
    List<BookingResponse> bookings
) {}
