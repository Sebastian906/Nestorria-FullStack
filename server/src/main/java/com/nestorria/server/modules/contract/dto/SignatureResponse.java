package com.nestorria.server.modules.contract.dto;

import java.time.Instant;

import com.nestorria.server.modules.contract.DigitalSignature;
import com.nestorria.server.modules.contract.SignatureRole;

public record SignatureResponse(
    String id,
    String userId,
    String userName,
    SignatureRole role,
    Instant signedAt
) {
    public static SignatureResponse fromEntity(DigitalSignature signature) {
        return new SignatureResponse(
            signature.getId(),
            signature.getUser().getId(),
            signature.getUser().getUsername(),
            signature.getRole(),
            signature.getSignedAt()
        );
    }
}
