package com.nestorria.server.modules.booking.dto;

import java.util.List;

public record AgencyDashboardResponse(
    int totalBookings,
    int totalRevenue,
    List<BookingResponse> bookings
) {}
