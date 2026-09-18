package fr.armand.backend.dto;

import java.time.Instant;
import fr.armand.backend.entity.TicketComment;

public record CommentResponse(Long id, Long ticketId, UserResponse author, String kind,
        String content, Instant createdAt) {
    public static CommentResponse from(TicketComment comment) {
        return new CommentResponse(comment.getId(), comment.getTicket().getId(),
                UserResponse.from(comment.getAuthor()), comment.getKind(), comment.getContent(), comment.getCreatedAt());
    }
}
