package com.nestorria.server.modules.properties;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.nestorria.server.common.persistence.Auditable;
import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.properties.embeddable.FacilityDetails;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "properties")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Property extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @NotBlank
    @Column(nullable = false)
    private String title;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @NotBlank
    @Column(nullable = false)
    private String city;

    @NotBlank
    @Column(nullable = false)
    private String country;

    @NotBlank
    @Column(nullable = false)
    private String address;

    @Min(1)
    @Column(nullable = false)
    private int area;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false)
    private PropertyType propertyType;

    @Embedded
    private PriceDetails price = new PriceDetails();

    @Embedded
    private FacilityDetails facilities = new FacilityDetails();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> amenities = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> images = new ArrayList<>();

    @Column(nullable = false)
    private boolean isAvailable = true;

    public Property(
            Agency agency,
            String title,
            String description,
            String city,
            String country,
            String address,
            int area,
            PropertyType propertyType,
            PriceDetails price,
            FacilityDetails facilities,
            List<String> amenities
    ) {
        this.agency = agency;
        this.title = title;
        this.description = description;
        this.city = city;
        this.country = country;
        this.address = address;
        this.area = area;
        this.propertyType = propertyType;
        this.price = price;
        this.facilities = facilities;
        this.amenities = amenities;
    }

    public void toggleAvailability() {
        this.isAvailable = !this.isAvailable;
    }
}
