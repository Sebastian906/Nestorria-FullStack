package com.nestorria.server.modules.contract.dto;

import com.nestorria.server.modules.contract.ContractClause;

public record ContractClauseResponse(
    String id,
    String title,
    String content,
    int sortOrder
) {
    public static ContractClauseResponse fromEntity(ContractClause clause) {
        return new ContractClauseResponse(
            clause.getId(),
            clause.getTitle(),
            clause.getContent(),
            clause.getSortOrder()
        );
    }
}
