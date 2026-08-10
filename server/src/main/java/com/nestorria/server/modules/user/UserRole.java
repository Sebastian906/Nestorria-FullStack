package com.nestorria.server.modules.user;

import java.util.Map;

public enum UserRole {
    USER("user"),
    AGENCY_OWNER("agency_owner"),
    MANAGER("manager"),
    ADMINISTRATOR("administrator");

    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /***
     * Returns the UserRole enum value for the given string.
     * @param value the string value
     * @return the corresponding UserRole
     * @throws IllegalArgumentException if the value is not recognized
     */
    private static final Map<String, UserRole> BY_VALUE = Map.of(
        "user",          UserRole.USER,
        "agency_owner",  UserRole.AGENCY_OWNER,
        "manager",       UserRole.MANAGER,
        "administrator", UserRole.ADMINISTRATOR
    );

    public static UserRole fromValue(String value) {
        UserRole role = BY_VALUE.get(value);
        if (role == null) {
            throw new IllegalArgumentException("Rol desconocido: " + value);
        }
        return role;
    }
}
