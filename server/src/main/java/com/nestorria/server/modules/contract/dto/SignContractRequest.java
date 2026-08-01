package com.nestorria.server.modules.contract.dto;

import com.nestorria.server.modules.contract.SignatureRole;

import jakarta.validation.constraints.NotNull;

public record SignContractRequest(
    @NotNull SignatureRole role
) {}
