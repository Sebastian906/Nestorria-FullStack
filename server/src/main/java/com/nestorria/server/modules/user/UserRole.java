package com.nestorria.server.modules.user;

public enum UserRole {
    USER("user"),
    AGENCY_OWNER("agency_owner");

    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static UserRole fromValue(String value) {
        for (UserRole role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Rol desconocido: " + value);
    }
}
