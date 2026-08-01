package com.nestorria.server.modules.contract;

import java.time.Instant;

import com.nestorria.server.common.persistence.Auditable;
import com.nestorria.server.modules.user.User;

import jakarta.persistence.Column;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "digital_signatures",
    uniqueConstraints = @UniqueConstraint(columnNames = {"contract_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DigitalSignature extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignatureRole role;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    public DigitalSignature(Contract contract, User user, SignatureRole role,
                            String ipAddress, String userAgent) {
        this.contract = contract;
        this.user = user;
        this.role = role;
        this.signedAt = Instant.now();
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }
}
