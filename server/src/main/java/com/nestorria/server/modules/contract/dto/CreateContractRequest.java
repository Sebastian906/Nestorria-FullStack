package com.nestorria.server.modules.contract.dto;

import com.nestorria.server.modules.contract.ContractType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateContractRequest(
    @NotBlank String bookingId,
    @NotNull ContractType contractType
) {}
