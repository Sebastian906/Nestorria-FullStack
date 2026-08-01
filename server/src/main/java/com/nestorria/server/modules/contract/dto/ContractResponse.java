package com.nestorria.server.modules.contract.dto;

import java.time.Instant;
import java.util.List;

import com.nestorria.server.modules.contract.Contract;
import com.nestorria.server.modules.contract.ContractStatus;
import com.nestorria.server.modules.contract.ContractType;

public record ContractResponse(
    String id,
    String bookingId,
    ContractType contractType,
    ContractStatus status,
    List<ContractClauseResponse> clauses,
    List<SignatureResponse> signatures,
    Instant generatedAt,
    Instant createdAt
) {
    public static ContractResponse fromEntity(Contract contract) {
        return new ContractResponse(
            contract.getId(),
            contract.getBooking().getId(),
            contract.getContractType(),
            contract.getStatus(),
            contract.getClauses().stream()
                .map(ContractClauseResponse::fromEntity)
                .toList(),
            contract.getSignatures().stream()
                .map(SignatureResponse::fromEntity)
                .toList(),
            contract.getGeneratedAt(),
            contract.getCreatedAt()
        );
    }
}
