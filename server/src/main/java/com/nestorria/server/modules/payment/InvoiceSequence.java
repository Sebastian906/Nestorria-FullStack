package com.nestorria.server.modules.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "invoice_sequences",
    uniqueConstraints = @UniqueConstraint(columnNames = {"year"})
)
@Getter
@Setter
@NoArgsConstructor
public class InvoiceSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private int year;

    @Column(nullable = false)
    private long nextValue = 1;

    public InvoiceSequence(int year) {
        this.year = year;
        this.nextValue = 1;
    }
}
