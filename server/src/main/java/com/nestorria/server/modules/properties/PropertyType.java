package com.nestorria.server.modules.properties;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PropertyType {
    HOUSE("House"),
    APARTMENT("Apartment"),
    VILLA("Villa"),
    PENTHOUSE("Penthouse"),
    TOWNHOUSE("Townhouse"),
    COMMERCIAL("Commercial"),
    LAND_PLOT("Land Plot");

    private final String displayName;

    PropertyType(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }
}