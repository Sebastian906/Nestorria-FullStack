package com.nestorria.server.modules.contract;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DigitalSignatureRepository extends JpaRepository<DigitalSignature, String> {

    boolean existsByContractIdAndUserId(String contractId, String userId);

    List<DigitalSignature> findByContractId(String contractId);
}
