package com.nestorria.server.modules.contract;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.nestorria.server.common.persistence.Auditable;
import com.nestorria.server.modules.booking.Booking;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "contracts",
    uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id"})
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contract extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false)
    private ContractType contractType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status;

    @Column(name = "signed_by_tenant_at")
    private Instant signedByTenantAt;

    @Column(name = "signed_by_agency_at")
    private Instant signedByAgencyAt;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ContractClause> clauses = new ArrayList<>();

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<DigitalSignature> signatures = new HashSet<>();

    public Contract(Booking booking, ContractType contractType) {
        this.booking = booking;
        this.contractType = contractType;
        this.status = ContractStatus.DRAFT;
    }

    public void markReadyForSignature() {
        this.status = ContractStatus.PENDING_SIGNATURE;
        this.generatedAt = Instant.now();
    }

    public void recordTenantSignature() {
        if (this.status != ContractStatus.PENDING_SIGNATURE) {
            throw new IllegalStateException("El contrato no está pendiente de firma");
        }
        this.signedByTenantAt = Instant.now();
        checkAndMarkSigned();
    }

    public void recordAgencySignature() {
        if (this.status != ContractStatus.PENDING_SIGNATURE) {
            throw new IllegalStateException("El contrato no está pendiente de firma");
        }
        this.signedByAgencyAt = Instant.now();
        checkAndMarkSigned();
    }

    private void checkAndMarkSigned() {
        if (this.signedByTenantAt != null && this.signedByAgencyAt != null) {
            this.status = ContractStatus.SIGNED;
        }
    }

    public boolean isParty(String userId) {
        return booking.getUser().getId().equals(userId)
            || booking.getAgency().getOwner().getId().equals(userId);
    }
}
