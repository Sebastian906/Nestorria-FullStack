package com.nestorria.server.modules.properties.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PriceDetails {
    @Column(name = "price_rent")
    private Integer rent;

    @Column(name = "price_sale")
    private Integer sale;
}
