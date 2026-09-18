package fr.armand.backend.service;

import java.util.List;
import fr.armand.backend.dto.UserResponse;
import fr.armand.backend.entity.AppUser;
import fr.armand.backend.exception.BusinessException;
import fr.armand.backend.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static fr.armand.backend.exception.BusinessException.Code.*;

/** currentUserId doit provenir de la session authentifiée, jamais du formulaire HTTP. */
@Service
@Transactional(readOnly = true)
public class AppUserService {
    private final AppUserRepository appUserRepository;

    public AppUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public UserResponse findCurrentUser(Long currentUserId) {
        return UserResponse.from(requireActiveUser(currentUserId));
    }

    public List<UserResponse> findActiveTechnicians(Long currentUserId) {
        requireTechnician(currentUserId);
        return appUserRepository.findByRoleAndActiveTrueOrderByDisplayNameAscIdAsc("TECHNICIEN")
                .stream().map(UserResponse::from).toList();
    }

    AppUser requireActiveUser(Long currentUserId) {
        if (currentUserId == null || currentUserId <= 0) {
            throw new BusinessException(UNAUTHENTICATED, "Connexion requise.");
        }
        AppUser user = appUserRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(UNAUTHENTICATED, "Compte connecté introuvable."));
        if (!user.isActive() || !("COLLABORATEUR".equals(user.getRole()) || "TECHNICIEN".equals(user.getRole()))) {
            throw new BusinessException(FORBIDDEN, "Ce compte ne peut pas accéder à l'application.");
        }
        return user;
    }

    AppUser requireTechnician(Long currentUserId) {
        AppUser user = requireActiveUser(currentUserId);
        if (!isTechnician(user)) {
            throw new BusinessException(FORBIDDEN, "Cette action est réservée aux techniciens.");
        }
        return user;
    }

    AppUser requireAssignableTechnician(Long technicianId) {
        InputValidation.id(technicianId, "Le technicien");
        AppUser technician = appUserRepository.findById(technicianId)
                .orElseThrow(() -> new BusinessException(NOT_FOUND, "Technicien introuvable."));
        if (!technician.isActive() || !isTechnician(technician)) {
            throw new BusinessException(INVALID_INPUT, "L'affectation exige un technicien actif.");
        }
        return technician;
    }

    boolean isTechnician(AppUser user) {
        return "TECHNICIEN".equals(user.getRole());
    }
}
