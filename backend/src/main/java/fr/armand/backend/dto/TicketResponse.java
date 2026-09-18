package fr.armand.backend.dto;

import java.time.Instant;
import fr.armand.backend.entity.Ticket;

public record TicketResponse(Long id, String title, String description, String status,
        UserResponse requester, UserResponse assignee, CategoryResponse category, String resolution,
        Instant createdAt, Instant updatedAt, Instant resolvedAt) {
    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(ticket.getId(), ticket.getTitle(), ticket.getDescription(), ticket.getStatus(),
                UserResponse.from(ticket.getRequester()),
                ticket.getAssignee() == null ? null : UserResponse.from(ticket.getAssignee()),
                CategoryResponse.from(ticket.getCategory()), ticket.getResolution(),
                ticket.getCreatedAt(), ticket.getUpdatedAt(), ticket.getResolvedAt());
    }
}
