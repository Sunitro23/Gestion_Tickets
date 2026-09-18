package fr.armand.backend.dto;

import fr.armand.backend.entity.Category;

public record CategoryResponse(Long id, String name, boolean active) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.isActive());
    }
}
