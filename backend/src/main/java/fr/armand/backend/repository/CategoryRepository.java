package fr.armand.backend.repository;

import fr.armand.backend.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByActiveTrueOrderByNameAscIdAsc();
}
