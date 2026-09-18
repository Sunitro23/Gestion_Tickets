package fr.armand.backend.service;

import java.util.List;
import fr.armand.backend.dto.CommentResponse;
import fr.armand.backend.entity.AppUser;
import fr.armand.backend.entity.Ticket;
import fr.armand.backend.entity.TicketComment;
import fr.armand.backend.repository.TicketCommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** currentUserId doit provenir de la session authentifiée, jamais du formulaire HTTP. */
@Service
@Transactional(readOnly = true)
public class TicketCommentService {
    private final TicketCommentRepository comments;
    private final AppUserService users;
    private final TicketAccess access;

    public TicketCommentService(TicketCommentRepository comments, AppUserService users, TicketAccess access) {
        this.comments = comments;
        this.users = users;
        this.access = access;
    }

    public List<CommentResponse> findByTicketId(Long currentUserId, Long ticketId) {
        AppUser actor = users.requireActiveUser(currentUserId);
        Ticket ticket = access.requireVisible(ticketId, actor);
        return comments.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId())
                .stream().map(CommentResponse::from).toList();
    }

    @Transactional
    public CommentResponse add(Long currentUserId, Long ticketId, String content) {
        AppUser actor = users.requireActiveUser(currentUserId);
        Ticket ticket = access.requireVisible(ticketId, actor);
        String cleanContent = InputValidation.text(content, "Le commentaire", 1, 5000);
        TicketComment comment = comments.save(new TicketComment(ticket, actor, cleanContent));
        ticket.touch();
        return CommentResponse.from(comment);
    }
}
