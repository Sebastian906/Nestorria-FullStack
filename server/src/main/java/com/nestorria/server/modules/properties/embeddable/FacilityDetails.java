package com.nestorria.server.modules.properties.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FacilityDetails {
    @Min(0)
    @Column(name = "facility_bedrooms", nullable = false)
    private int bedrooms;

    @Min(0)
    @Column(name = "facility_bathrooms", nullable = false)
    private int bathrooms;

    @Min(0)
    @Column(name = "facility_garages", nullable = false)
    private int garages;
}
