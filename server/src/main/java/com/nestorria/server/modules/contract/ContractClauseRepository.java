package com.nestorria.server.modules.contract;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractClauseRepository extends JpaRepository<ContractClause, String> {

    List<ContractClause> findByContractIdOrderBySortOrder(String contractId);
}
