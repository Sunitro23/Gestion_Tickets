package fr.armand.backend.service;

import fr.armand.backend.entity.AppUser;
import fr.armand.backend.entity.Ticket;
import fr.armand.backend.exception.BusinessException;
import fr.armand.backend.repository.TicketRepository;
import org.springframework.stereotype.Component;
import static fr.armand.backend.exception.BusinessException.Code.NOT_FOUND;

// Même contrôle de visibilité pour les tickets et leurs commentaires.
@Component
class TicketAccess {
    private final TicketRepository tickets;
    private final AppUserService users;

    TicketAccess(TicketRepository tickets, AppUserService users) {
        this.tickets = tickets;
        this.users = users;
    }

    Ticket requireVisible(Long ticketId, AppUser actor) {
        InputValidation.id(ticketId, "Le ticket");
        Ticket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new BusinessException(NOT_FOUND, "Ticket introuvable."));
        if (!users.isTechnician(actor) && !ticket.getRequester().getId().equals(actor.getId())) {
            // Ne pas révéler l'existence d'un ticket appartenant à quelqu'un d'autre.
            throw new BusinessException(NOT_FOUND, "Ticket introuvable.");
        }
        return ticket;
    }
}
