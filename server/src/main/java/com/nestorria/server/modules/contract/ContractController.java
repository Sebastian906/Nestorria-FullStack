package com.nestorria.server.modules.contract;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nestorria.server.modules.contract.dto.ContractResponse;
import com.nestorria.server.modules.contract.dto.ContractSummaryResponse;
import com.nestorria.server.modules.contract.dto.CreateContractRequest;
import com.nestorria.server.modules.contract.dto.SignContractRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/contracts")
@Tag(name = "Contracts", description = "Gestión de contratos digitales")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @Operation(summary = "Crear un contrato digital para una reserva confirmada")
    @PostMapping
    public ResponseEntity<ContractResponse> createContract(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateContractRequest request) {
        ContractResponse response = contractService.createContract(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Obtener un contrato completo con cláusulas y firmas")
    @GetMapping("/{id}")
    public ContractResponse getContract(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        return contractService.getContract(id, jwt.getSubject());
    }

    @Operation(summary = "Firmar un contrato digitalmente")
    @PostMapping("/{id}/sign")
    public ContractResponse signContract(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody SignContractRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        return contractService.signContract(
            id, jwt.getSubject(), request, ipAddress, userAgent);
    }

    @Operation(summary = "Obtener los contratos donde el usuario es inquilino")
    @GetMapping("/me")
    public List<ContractSummaryResponse> getMyContracts(@AuthenticationPrincipal Jwt jwt) {
        return contractService.getUserContracts(jwt.getSubject());
    }

    @Operation(summary = "Obtener los contratos de la agencia del usuario")
    @GetMapping("/agency")
    public List<ContractSummaryResponse> getAgencyContracts(@AuthenticationPrincipal Jwt jwt) {
        return contractService.getAgencyContracts(jwt.getSubject());
    }
}
