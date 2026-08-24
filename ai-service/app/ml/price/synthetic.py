"""Synthetic dataset generator for development and testing.

DO NOT use for production evaluation.
Generates realistic property data based on real-world distributions.
"""

import random

import structlog

logger = structlog.get_logger("ai-service.ml.price")

# Realistic distributions based on Spanish market
CITIES = ["Madrid", "Barcelona", "Valencia", "Sevilla", "Bilbao", "Málaga", "Zaragoza"]
PROPERTY_TYPES = ["HOUSE", "APARTMENT", "VILLA", "TOWNHOUSE", "PENTHOUSE"]
AMENITIES_POOL = ["parking", "pool", "garden", "terrace", "gym", "elevator", "storage"]

# Price ranges by type (EUR)
PRICE_RANGES = {
    "HOUSE": (150000, 500000),
    "APARTMENT": (100000, 350000),
    "VILLA": (300000, 800000),
    "TOWNHOUSE": (180000, 400000),
    "PENTHOUSE": (250000, 600000),
}

# Area ranges by type (m²)
AREA_RANGES = {
    "HOUSE": (80, 250),
    "APARTMENT": (40, 150),
    "VILLA": (150, 400),
    "TOWNHOUSE": (70, 180),
    "PENTHOUSE": (60, 200),
}

def generate_synthetic_properties(n: int = 500, seed: int = 42) -> list[dict]:
    """Generate n synthetic property records.

    Returns list of dicts matching PropertyFeatureExtractor input format.
    """
    random.seed(seed)
    properties = []

    for _ in range(n):
        prop_type = random.choice(PROPERTY_TYPES)
        city = random.choice(CITIES)
        area_min, area_max = AREA_RANGES[prop_type]
        area = random.randint(area_min, area_max)
        bedrooms = max(1, area // 40 + random.randint(-1, 1))
        bathrooms = max(1, bedrooms // 2 + random.randint(0, 1))

        # Price correlates with area, type, and city
        price_min, price_max = PRICE_RANGES[prop_type]
        base_price = price_min + (price_max - price_min) * (area / area_max)
        city_multiplier = {"Madrid": 1.3, "Barcelona": 1.25, "Valencia": 0.9}.get(city, 1.0)
        price = int(base_price * city_multiplier * random.uniform(0.85, 1.15))

        # Amenities (random subset)
        n_amenities = random.randint(0, 4)
        amenities = random.sample(AMENITIES_POOL, min(n_amenities, len(AMENITIES_POOL)))

        properties.append({
            "area": area,
            "propertyType": prop_type,
            "city": city,
            "bedrooms": bedrooms,
            "bathrooms": bathrooms,
            "latitude": round(random.uniform(36.0, 43.5), 6),
            "longitude": round(random.uniform(-9.0, 3.0), 6),
            "amenities": amenities,
        })

    logger.info("synthetic_generated", count=len(properties))
    return properties

def generate_synthetic_dataset(
    n: int = 500, seed: int = 42
) -> tuple[list[dict], list[float]]:
    """Generate synthetic properties with corresponding prices.

    Returns:
        properties: list of property dicts
        prices: list of float prices
    """
    properties = generate_synthetic_properties(n, seed)
    prices = []

    for p in properties:
        ptype = p["propertyType"]
        area = p["area"]
        price_min, price_max = PRICE_RANGES[ptype]
        base = price_min + (price_max - price_min) * (area / AREA_RANGES[ptype][1])
        city_mult = {"Madrid": 1.3, "Barcelona": 1.25, "Valencia": 0.9}.get(p["city"], 1.0)
        price = base * city_mult * random.uniform(0.85, 1.15)
        prices.append(round(price, 2))

    return properties, prices