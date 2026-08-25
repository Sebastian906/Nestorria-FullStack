"""Synthetic dataset generator for cancellation prediction.

DO NOT use for production evaluation.
Generates realistic booking data based on real-world distributions.
"""

import random
from datetime import date, timedelta

import structlog

logger = structlog.get_logger("ai-service.ml.cancellation")

CITIES = ["Madrid", "Barcelona", "Valencia", "Sevilla", "Bilbao", "Málaga", "Zaragoza"]
PROPERTY_TYPES = ["HOUSE", "APARTMENT", "VILLA", "TOWNHOUSE", "PENTHOUSE"]

# Cancellation rates by property type (synthetic)
CANCEL_RATES = {
    "HOUSE": 0.08,
    "APARTMENT": 0.12,
    "VILLA": 0.05,
    "TOWNHOUSE": 0.10,
    "PENTHOUSE": 0.07,
}

# Price ranges by type (EUR per night)
NIGHTLY_RATES = {
    "HOUSE": (80, 250),
    "APARTMENT": (50, 180),
    "VILLA": (150, 400),
    "TOWNHOUSE": (90, 220),
    "PENTHOUSE": (120, 350),
}

def generate_synthetic_cancellations(
    n: int = 200,
    cancel_ratio: float = 0.15,
    seed: int = 42,
) -> tuple[list[dict], list[int]]:
    """Generate synthetic booking data with cancellation labels.

    Labels are derived from booking features using CANCEL_RATES
    and a risk function, not from the loop index.

    Args:
        n: total number of bookings
        cancel_ratio: target fraction of cancelled bookings (used as
            a scaling factor on top of per-type base rates)
        seed: random seed for reproducibility

    Returns:
        bookings: list of booking dicts matching Spring Boot features format
        labels: list of binary labels (1=cancelled, 0=not cancelled)
    """
    random.seed(seed)
    bookings = []

    for _ in range(n):
        prop_type = random.choice(PROPERTY_TYPES)
        city = random.choice(CITIES)

        # Dates
        base_date = date.today() + timedelta(days=random.randint(1, 90))
        stay_nights = random.randint(1, 14)
        check_in = base_date
        check_out = base_date + timedelta(days=stay_nights)

        # Price
        rate_min, rate_max = NIGHTLY_RATES[prop_type]
        nightly_rate = random.randint(rate_min, rate_max)
        total_price = nightly_rate * stay_nights

        # Guests
        guests = random.randint(1, 8)

        booking = {
            "checkInDate": check_in.isoformat(),
            "checkOutDate": check_out.isoformat(),
            "guests": guests,
            "totalPrice": total_price,
            "propertyType": prop_type,
            "city": city,
        }

        bookings.append(booking)

    # Derive labels from features using CANCEL_RATES + cancel_ratio scaling
    base_rate = cancel_ratio
    labels = []
    for b in bookings:
        type_rate = CANCEL_RATES.get(b["propertyType"], 0.10)
        # Blend per-type rate with global target; clamp to [0.01, 0.99]
        blended = max(0.01, min(0.99, (type_rate + base_rate) / 2))
        is_cancelled = random.random() < blended
        labels.append(1 if is_cancelled else 0)

    # Shuffle together
    combined = list(zip(bookings, labels))
    random.shuffle(combined)
    bookings, labels = zip(*combined)

    logger.info(
        "synthetic_cancellations_generated",
        count=len(bookings),
        cancelled=sum(labels),
        not_cancelled=len(labels) - sum(labels),
    )

    return list(bookings), list(labels)