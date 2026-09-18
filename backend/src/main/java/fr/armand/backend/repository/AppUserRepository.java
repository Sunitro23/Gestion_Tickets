package fr.armand.backend.repository;

import fr.armand.backend.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    List<AppUser> findByRoleAndActiveTrueOrderByDisplayNameAscIdAsc(String role);
}
