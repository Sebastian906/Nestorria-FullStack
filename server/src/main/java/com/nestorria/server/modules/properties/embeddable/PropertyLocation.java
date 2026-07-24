package com.nestorria.server.modules.properties.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PropertyLocation {

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    @Column(name = "location_latitude")
    private Double latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    @Column(name = "location_longitude")
    private Double longitude;

    @Column(name = "location_neighborhood")
    private String neighborhood;

    @Column(name = "location_postal_code")
    private String postalCode;
}
