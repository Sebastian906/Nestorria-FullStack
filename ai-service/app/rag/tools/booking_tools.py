"""Booking query tools."""

from __future__ import annotations

from app.rag.tools.base import BaseTool

class GetBookingStats(BaseTool):
    """Get booking statistics."""
    name = "get_booking_stats"
    description = "Get booking statistics for Nestorria: total bookings, total revenue from paid bookings, and breakdown by status."
    parameters = {
        "type": "object",
        "properties": {},
        "required": [],
    }