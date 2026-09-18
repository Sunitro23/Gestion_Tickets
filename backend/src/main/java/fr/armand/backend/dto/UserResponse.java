package fr.armand.backend.dto;

import fr.armand.backend.entity.AppUser;

// Un DTO ne contient que les informations qui peuvent sortir du service.
public record UserResponse(Long id, String displayName, String email, String role, boolean active) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isActive());
    }
}
