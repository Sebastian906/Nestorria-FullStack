package com.nestorria.server.modules.contract.dto;

import java.time.Instant;

import com.nestorria.server.modules.contract.Contract;
import com.nestorria.server.modules.contract.ContractStatus;
import com.nestorria.server.modules.contract.ContractType;

public record ContractSummaryResponse(
    String id,
    String bookingId,
    ContractType contractType,
    ContractStatus status,
    String propertyTitle,
    boolean signedByTenant,
    boolean signedByAgency,
    Instant generatedAt
) {
    public static ContractSummaryResponse fromEntity(Contract contract) {
        return new ContractSummaryResponse(
            contract.getId(),
            contract.getBooking().getId(),
            contract.getContractType(),
            contract.getStatus(),
            contract.getBooking().getProperty().getTitle(),
            contract.getSignedByTenantAt() != null,
            contract.getSignedByAgencyAt() != null,
            contract.getGeneratedAt()
        );
    }
}
