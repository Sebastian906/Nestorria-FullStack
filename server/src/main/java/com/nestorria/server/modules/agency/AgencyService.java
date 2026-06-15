package com.nestorria.server.modules.agency;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.exception.ConflictException;
import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.agency.dto.AgencyRegistrationRequest;
import com.nestorria.server.modules.agency.dto.AgencyResponse;
import com.nestorria.server.modules.user.User;
import com.nestorria.server.modules.user.UserRepository;
import com.nestorria.server.modules.user.UserRole;

@Service
public class AgencyService {
    private final AgencyRepository agencyRepository;
    private final UserRepository userRepository;

    public AgencyService(AgencyRepository agencyRepository, UserRepository userRepository) {
        this.agencyRepository = agencyRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AgencyResponse register(String userId, AgencyRegistrationRequest request) {
        if (agencyRepository.existsByOwnerId(userId)) {
            throw new ConflictException("El usuario ya tiene una agencia registrada");
        }

        User owner = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userId));

        Agency agency = new Agency(
            request.name(),
            request.address(),
            request.contact(),
            request.email(),
            request.city(),
            owner
        );

        owner.setRole(UserRole.AGENCY_OWNER);
        userRepository.save(owner);

        try {
            Agency saved = agencyRepository.save(agency);
            return AgencyResponse.fromEntity(saved);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ConflictException("El usuario ya tiene una agencia registrada");
        }
    }

    @Transactional(readOnly = true)
    public AgencyResponse getMyAgency(String userId) {
        Agency agency = agencyRepository.findByOwnerId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("El usuario no tiene una agencia registrada"));
        return AgencyResponse.fromEntity(agency);
    }
}
