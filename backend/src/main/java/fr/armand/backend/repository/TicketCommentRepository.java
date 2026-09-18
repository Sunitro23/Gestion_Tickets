package fr.armand.backend.repository;

import fr.armand.backend.entity.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {
    List<TicketComment> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);
}
