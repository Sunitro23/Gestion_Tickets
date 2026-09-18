package fr.armand.backend.service;

import java.util.List;
import fr.armand.backend.dto.CategoryResponse;
import fr.armand.backend.entity.Category;
import fr.armand.backend.exception.BusinessException;
import fr.armand.backend.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static fr.armand.backend.exception.BusinessException.Code.*;

/** currentUserId doit provenir de la session authentifiée. */
@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final AppUserService users;

    public CategoryService(CategoryRepository categoryRepository, AppUserService users) {
        this.categoryRepository = categoryRepository;
        this.users = users;
    }

    public List<CategoryResponse> findActive(Long currentUserId) {
        users.requireActiveUser(currentUserId);
        return categoryRepository.findByActiveTrueOrderByNameAscIdAsc()
                .stream().map(CategoryResponse::from).toList();
    }

    Category requireActiveCategory(Long categoryId) {
        InputValidation.id(categoryId, "La catégorie");
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(NOT_FOUND, "Catégorie introuvable."));
        if (!category.isActive()) {
            throw new BusinessException(INVALID_INPUT, "La catégorie est désactivée.");
        }
        return category;
    }
}
