package com.nestorria.server.modules.agency.dto;

import com.nestorria.server.modules.agency.Agency;

public record AgencyResponse(
    String id,
    String name,
    String address,
    String contact,
    String email,
    String city,
    String ownerId
) {
    public static AgencyResponse fromEntity(Agency agency) {
        return new AgencyResponse(
            agency.getId(),
            agency.getName(),
            agency.getAddress(),
            agency.getContact(),
            agency.getEmail(),
            agency.getCity(),
            agency.getOwner().getId()
        );
    }
}
