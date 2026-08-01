package com.nestorria.server.modules.contract;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.event.NotificationEvent;
import com.nestorria.server.common.exception.BadRequestException;
import com.nestorria.server.common.exception.ConflictException;
import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.booking.Booking;
import com.nestorria.server.modules.booking.BookingRepository;
import com.nestorria.server.modules.booking.BookingStatus;
import com.nestorria.server.modules.contract.dto.ContractResponse;
import com.nestorria.server.modules.contract.dto.ContractSummaryResponse;
import com.nestorria.server.modules.contract.dto.CreateContractRequest;
import com.nestorria.server.modules.contract.dto.SignContractRequest;
import com.nestorria.server.modules.notification.NotificationType;
import com.nestorria.server.modules.user.User;
import com.nestorria.server.modules.user.UserRepository;

@Service
public class ContractService {

    private final ContractRepository contractRepository;
    private final ContractClauseRepository contractClauseRepository;
    private final DigitalSignatureRepository digitalSignatureRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ContractService(
            ContractRepository contractRepository,
            ContractClauseRepository contractClauseRepository,
            DigitalSignatureRepository digitalSignatureRepository,
            BookingRepository bookingRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.contractRepository = contractRepository;
        this.contractClauseRepository = contractClauseRepository;
        this.digitalSignatureRepository = digitalSignatureRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ContractResponse createContract(String userId, CreateContractRequest request) {
        Booking booking = bookingRepository.findById(request.bookingId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Reserva no encontrada: " + request.bookingId()));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BadRequestException(
                "Solo se pueden crear contratos para reservas confirmadas. Estado actual: " + booking.getStatus());
        }

        if (!contractRepository.existsByBookingId(booking.getId())) {
            // No contract exists — proceed
        } else {
            throw new ConflictException(
                "Ya existe un contrato para la reserva: " + booking.getId());
        }

        // Validate user is a party to the booking
        String tenantId = booking.getUser().getId();
        String agencyOwnerId = booking.getAgency().getOwner().getId();
        if (!userId.equals(tenantId) && !userId.equals(agencyOwnerId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "No eres parte de esta reserva");
        }

        Contract contract = new Contract(booking, request.contractType());
        contract.markReadyForSignature();
        Contract savedContract = contractRepository.save(contract);

        // Generate default clauses
        List<ContractClause> clauses = generateDefaultClauses(savedContract, request.contractType());
        contractClauseRepository.saveAll(clauses);
        savedContract.getClauses().addAll(clauses);

        return ContractResponse.fromEntity(savedContract);
    }

    @Transactional(readOnly = true)
    public ContractResponse getContract(String contractId, String userId) {
        Contract contract = contractRepository.findByIdWithDetails(contractId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Contrato no encontrado: " + contractId));

        if (!contract.isParty(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "No tienes acceso a este contrato");
        }

        return ContractResponse.fromEntity(contract);
    }

    @Transactional
    public ContractResponse signContract(String contractId, String userId,
                                          SignContractRequest request,
                                          String ipAddress, String userAgent) {
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Contrato no encontrado: " + contractId));

        if (contract.getStatus() != ContractStatus.PENDING_SIGNATURE) {
            throw new BadRequestException(
                "El contrato no está pendiente de firma. Estado actual: " + contract.getStatus());
        }

        Booking booking = contract.getBooking();
        String tenantId = booking.getUser().getId();
        String agencyOwnerId = booking.getAgency().getOwner().getId();

        // Validate user is a party to the booking
        if (!userId.equals(tenantId) && !userId.equals(agencyOwnerId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "No eres parte de este contrato");
        }

        // Validate declared role matches actual role
        SignatureRole actualRole = userId.equals(tenantId)
            ? SignatureRole.TENANT
            : SignatureRole.AGENCY_OWNER;

        if (request.role() != actualRole) {
            throw new BadRequestException(
                "El rol declarado no coincide con tu rol en la reserva");
        }

        // Check not already signed
        if (digitalSignatureRepository.existsByContractIdAndUserId(contractId, userId)) {
            throw new ConflictException("Ya has firmado este contrato");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Usuario no encontrado: " + userId));

        DigitalSignature signature = new DigitalSignature(
            contract, user, actualRole, ipAddress, userAgent);
        digitalSignatureRepository.save(signature);
        contract.getSignatures().add(signature);

        // Record signature and possibly transition to SIGNED
        if (actualRole == SignatureRole.TENANT) {
            contract.recordTenantSignature();
        } else {
            contract.recordAgencySignature();
        }

        contractRepository.save(contract);

        // Publish notification events
        publishSigningNotifications(contract, actualRole, booking, tenantId, agencyOwnerId);

        return ContractResponse.fromEntity(contract);
    }

    @Transactional(readOnly = true)
    public List<ContractSummaryResponse> getUserContracts(String userId) {
        return contractRepository.findSummaryByBookingUserId(userId)
            .stream()
            .map(ContractSummaryResponse::fromEntity)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ContractSummaryResponse> getAgencyContracts(String userId) {
        return contractRepository.findSummaryByBookingAgencyOwnerId(userId)
            .stream()
            .map(ContractSummaryResponse::fromEntity)
            .toList();
    }

    private void publishSigningNotifications(Contract contract, SignatureRole signerRole,
                                             Booking booking,
            String tenantId, String agencyOwnerId) {
        String propertyAddress = booking.getProperty().getAddress();
        String contractId = contract.getId();
        boolean bothSigned = contract.getStatus() == ContractStatus.SIGNED;

        String messageTemplate = signerRole == SignatureRole.TENANT
                ? "El inquilino ha firmado el contrato para la propiedad en %s."
                : "La agencia ha firmado el contrato para la propiedad en %s.";

        String message = messageTemplate.formatted(propertyAddress);

        if (bothSigned) {
            String completionMessage = "El contrato para la propiedad en %s ha sido firmado por ambas partes."
                    .formatted(
                            propertyAddress);
            notifyContractSigned(tenantId, completionMessage, contractId);
            notifyContractSigned(agencyOwnerId, completionMessage, contractId);
        } else {
            String recipientId = signerRole == SignatureRole.TENANT ? agencyOwnerId : tenantId;
            notifyContractSigned(recipientId, message, contractId);
        }
    }

    private void notifyContractSigned(String recipientId, String message, String contractId) {
        eventPublisher.publishEvent(new NotificationEvent(
            recipientId,
            NotificationType.CONTRACT_SIGNED,
            NotificationType.CONTRACT_SIGNED.defaultTitle(),
            message,
            "contract",
            contractId
        ));
    }

    private List<ContractClause> generateDefaultClauses(Contract contract, ContractType type) {
        return switch (type) {
            case RENTAL -> List.of(
                new ContractClause(contract,
                    "Horario de entrada y salida",
                    "El inquilino podrá acceder a la propiedad a partir de las 15:00 horas "
                    + "y deberá desocuparla a más tardar a las 12:00 horas del día de salida.",
                    1),
                new ContractClause(contract,
                    "Política de cancelación",
                    "El inquilino podrá cancelar la reserva sin penalización hasta 48 horas antes "
                    + "de la fecha de entrada. Cancelaciones posteriores generarán un cargo del 50% "
                    + "del monto total.",
                    2),
                new ContractClause(contract,
                    "Responsabilidad por daños",
                    "El inquilino se compromete a mantener la propiedad en buen estado. "
                    + "Cualquier daño que exceda el desgaste natural será descontado del depósito de garantía.",
                    3),
                new ContractClause(contract,
                    "Número máximo de huéspedes",
                    "El número de huéspedes no podrá exceder lo establecido en la reserva. "
                    + "El exceso de huéspedes podrá resultar en la terminación del contrato.",
                    4),
                new ContractClause(contract,
                    "Uso de la propiedad",
                    "La propiedad será utilizada exclusivamente con fines residenciales. "
                    + "No se permiten fiestas, eventos ni actividades comerciales sin autorización previa.",
                    5)
            );
            case PURCHASE -> List.of(
                new ContractClause(contract,
                    "Descripción de la propiedad",
                    "La propiedad objeto de esta compraventa se describe detalladamente "
                    + "en el anexo correspondiente, incluyendo superficie, ubicación y características.",
                    1),
                new ContractClause(contract,
                    "Precio y forma de pago",
                    "El precio total de la transacción será el indicado en esta reserva. "
                    + "La forma de pago se acordará entre las partes de mutuo acuerdo.",
                    2),
                new ContractClause(contract,
                    "Inspección de la propiedad",
                    "El comprador tiene derecho a inspeccionar la propiedad antes de la "
                    + "formalización definitiva del contrato.",
                    3),
                new ContractClause(contract,
                    "Documentación",
                    "Ambas partes se comprometen a proporcionar la documentación necesaria "
                    + "para la formalización de la compraventa dentro de los plazos acordados.",
                    4),
                new ContractClause(contract,
                    "Condiciones generales",
                    "Este contrato se rige por la legislación vigente. Cualquier controversia "
                    + "será resuelta por los tribunales competentes.",
                    5)
            );
        };
    }
}
