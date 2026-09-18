package fr.armand.backend.repository;

import fr.armand.backend.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    @Query("""
            select t from Ticket t
            where (:requesterId is null or t.requester.id = :requesterId)
              and (:status is null or t.status = :status)
              and (:categoryId is null or t.category.id = :categoryId)
            order by t.createdAt desc, t.id desc
            """)
    List<Ticket> findVisibleTickets(@Param("requesterId") Long requesterId,
            @Param("status") String status, @Param("categoryId") Long categoryId);
}
