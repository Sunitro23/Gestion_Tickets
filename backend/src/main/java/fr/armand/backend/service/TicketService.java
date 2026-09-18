package fr.armand.backend.service;

import java.util.List;
import java.util.Set;
import fr.armand.backend.dto.TicketResponse;
import fr.armand.backend.entity.AppUser;
import fr.armand.backend.entity.Ticket;
import fr.armand.backend.entity.TicketComment;
import fr.armand.backend.exception.BusinessException;
import fr.armand.backend.repository.TicketRepository;
import fr.armand.backend.repository.TicketCommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static fr.armand.backend.exception.BusinessException.Code.*;

/** currentUserId doit provenir de la session authentifiée, jamais du formulaire HTTP. */
@Service
@Transactional(readOnly = true)
public class TicketService {
    private final TicketRepository tickets;
    private final TicketCommentRepository comments;
    private final AppUserService users;
    private final CategoryService categories;
    private final TicketAccess access;

    public TicketService(TicketRepository tickets, TicketCommentRepository comments,
            AppUserService users, CategoryService categories, TicketAccess access) {
        this.tickets = tickets;
        this.comments = comments;
        this.users = users;
        this.categories = categories;
        this.access = access;
    }

    public List<TicketResponse> findAll(Long currentUserId, String status, Long categoryId) {
        AppUser actor = users.requireActiveUser(currentUserId);
        if (status != null && !Set.of("OUVERTE", "EN_COURS", "RESOLUE").contains(status)) {
            throw new BusinessException(INVALID_INPUT, "Statut inconnu.");
        }
        if (categoryId != null) {
            InputValidation.id(categoryId, "La catégorie");
        }
        Long requesterId = users.isTechnician(actor) ? null : actor.getId();
        return tickets.findVisibleTickets(requesterId, status, categoryId)
                .stream().map(TicketResponse::from).toList();
    }

    public TicketResponse findById(Long currentUserId, Long ticketId) {
        AppUser actor = users.requireActiveUser(currentUserId);
        return TicketResponse.from(access.requireVisible(ticketId, actor));
    }

    @Transactional
    public TicketResponse create(Long currentUserId, String title, String description, Long categoryId) {
        AppUser actor = users.requireActiveUser(currentUserId);
        String cleanTitle = InputValidation.text(title, "Le titre", 3, 150);
        String cleanDescription = InputValidation.text(description, "La description", 10, 5000);
        Ticket ticket = new Ticket(cleanTitle, cleanDescription, actor, categories.requireActiveCategory(categoryId));
        return TicketResponse.from(tickets.save(ticket));
    }

    @Transactional
    public TicketResponse assign(Long currentUserId, Long ticketId, Long technicianId) {
        AppUser actor = users.requireTechnician(currentUserId);
        Ticket ticket = access.requireVisible(ticketId, actor);
        if (!Set.of("OUVERTE", "EN_COURS").contains(ticket.getStatus())) {
            throw new BusinessException(CONFLICT, "Rouvrez le ticket avant de modifier son affectation.");
        }
        if (technicianId == null && "EN_COURS".equals(ticket.getStatus())) {
            throw new BusinessException(CONFLICT, "Un ticket en cours doit conserver un technicien.");
        }
        ticket.assignTo(technicianId == null ? null : users.requireAssignableTechnician(technicianId));
        return TicketResponse.from(ticket);
    }

    @Transactional
    public TicketResponse start(Long currentUserId, Long ticketId) {
        AppUser actor = users.requireTechnician(currentUserId);
        Ticket ticket = access.requireVisible(ticketId, actor);
        requireStatus(ticket, "OUVERTE");
        requireActiveAssignee(ticket);
        ticket.start();
        return TicketResponse.from(ticket);
    }

    @Transactional
    public TicketResponse resolve(Long currentUserId, Long ticketId, String explanation) {
        AppUser actor = users.requireTechnician(currentUserId);
        Ticket ticket = access.requireVisible(ticketId, actor);
        requireStatus(ticket, "EN_COURS");
        requireActiveAssignee(ticket);
        ticket.resolve(InputValidation.text(explanation, "La résolution", 10, 5000));
        return TicketResponse.from(ticket);
    }

    @Transactional
    public TicketResponse reopen(Long currentUserId, Long ticketId, String reason) {
        AppUser actor = users.requireActiveUser(currentUserId);
        Ticket ticket = access.requireVisible(ticketId, actor);
        requireStatus(ticket, "RESOLUE");
        String cleanReason = InputValidation.text(reason, "Le motif de réouverture", 10, 5000);
        // Les commentaires et la modification du ticket appartiennent à la même transaction.
        List<TicketComment> history = List.of(TicketComment.archivedResolution(ticket, actor),
                TicketComment.reopening(ticket, actor, cleanReason));
        ticket.reopen();
        comments.saveAll(history);
        return TicketResponse.from(ticket);
    }

    private void requireStatus(Ticket ticket, String expected) {
        if (!expected.equals(ticket.getStatus())) {
            throw new BusinessException(CONFLICT, "Cette action exige le statut " + expected + ".");
        }
    }

    private void requireActiveAssignee(Ticket ticket) {
        AppUser assignee = ticket.getAssignee();
        if (assignee == null || !assignee.isActive() || !users.isTechnician(assignee)) {
            throw new BusinessException(CONFLICT, "Un technicien actif doit être affecté au ticket.");
        }
    }
}
