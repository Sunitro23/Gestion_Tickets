package fr.armand.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import fr.armand.backend.dto.TicketResponse;
import fr.armand.backend.entity.AppUser;
import fr.armand.backend.entity.Category;
import fr.armand.backend.entity.TicketComment;
import fr.armand.backend.exception.BusinessException;
import fr.armand.backend.repository.AppUserRepository;
import fr.armand.backend.repository.CategoryRepository;
import fr.armand.backend.repository.TicketCommentRepository;
import fr.armand.backend.repository.TicketRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static fr.armand.backend.exception.BusinessException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@SpringBootTest
@Transactional
class ServicesIntegrationTests {
    @Autowired TicketService tickets;
    @Autowired TicketCommentService comments;
    @Autowired AppUserService users;
    @Autowired CategoryService categories;
    @Autowired AppUserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TicketRepository ticketRepository;
    @MockitoSpyBean TicketCommentRepository commentRepository;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private AppUser owner;
    private AppUser other;
    private AppUser technician;
    private AppUser anotherTechnician;
    private Category category;

    @BeforeEach
    void prepareData() {
        String suffix = UUID.randomUUID().toString();
        owner = userRepository.save(new AppUser("Camille", "owner-" + suffix + "@example.test", "test-hash", "COLLABORATEUR"));
        other = userRepository.save(new AppUser("Noa", "other-" + suffix + "@example.test", "test-hash", "COLLABORATEUR"));
        technician = userRepository.save(new AppUser("Alex", "tech-" + suffix + "@example.test", "test-hash", "TECHNICIEN"));
        anotherTechnician = userRepository.save(new AppUser("Sam", "tech2-" + suffix + "@example.test", "test-hash", "TECHNICIEN"));
        category = categoryRepository.save(new Category("Test-" + suffix));
    }

    private TicketResponse create() {
        return tickets.create(owner.getId(), "  Imprimante inaccessible  ", "  Impossible de lancer une impression.  ", category.getId());
    }

    private TicketResponse resolved() {
        TicketResponse ticket = create();
        tickets.assign(technician.getId(), ticket.id(), technician.getId());
        tickets.start(anotherTechnician.getId(), ticket.id());
        return tickets.resolve(anotherTechnician.getId(), ticket.id(), "Le pilote a été réinstallé.");
    }

    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }

    private void expectError(BusinessException.Code code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(code));
    }

    @Test
    void createsOpenTicketForCurrentUserAndPersistsDefaults() {
        TicketResponse created = create();
        reload();
        TicketResponse stored = tickets.findById(owner.getId(), created.id());
        assertThat(stored.title()).isEqualTo("Imprimante inaccessible");
        assertThat(stored.description()).isEqualTo("Impossible de lancer une impression.");
        assertThat(stored.requester().id()).isEqualTo(owner.getId());
        assertThat(stored.status()).isEqualTo("OUVERTE");
        assertThat(stored.assignee()).isNull();
        assertThat(stored.resolution()).isNull();
        assertThat(stored.resolvedAt()).isNull();
        assertThat(stored.createdAt()).isNotNull().isEqualTo(stored.updatedAt());
        assertThat(stored.category().id()).isEqualTo(category.getId());
    }

    static Stream<Arguments> invalidTicketTexts() {
        return Stream.of(
                Arguments.of(null, "Description valide"),
                Arguments.of("   ", "Description valide"),
                Arguments.of("ab", "Description valide"),
                Arguments.of("a".repeat(151), "Description valide"),
                Arguments.of("Titre", null),
                Arguments.of("Titre", " \t\n "),
                Arguments.of("Titre", "a".repeat(9)),
                Arguments.of("Titre", "a".repeat(5001)));
    }

    @ParameterizedTest
    @MethodSource("invalidTicketTexts")
    void rejectsInvalidTicketText(String title, String description) {
        expectError(INVALID_INPUT, () -> tickets.create(owner.getId(), title, description, category.getId()));
    }

    @Test
    void acceptsTextLengthBoundaries() {
        assertThat(tickets.create(owner.getId(), "abc", "a".repeat(10), category.getId()).id()).isNotNull();
        assertThat(tickets.create(owner.getId(), "a".repeat(150), "a".repeat(5000), category.getId()).id()).isNotNull();
    }

    @Test
    void rejectsMissingAndInactiveCategories() {
        expectError(INVALID_INPUT, () -> tickets.create(owner.getId(), "Titre", "Description valide", null));
        expectError(NOT_FOUND, () -> tickets.create(owner.getId(), "Titre", "Description valide", Long.MAX_VALUE));
        jdbc.update("update category set active = false where id = ?", category.getId());
        entityManager.clear();
        expectError(INVALID_INPUT, () -> tickets.create(owner.getId(), "Titre", "Description valide", category.getId()));
        assertThat(categories.findActive(owner.getId())).extracting(c -> c.id()).doesNotContain(category.getId());
    }

    @Test
    void inactiveCategoryRemainsVisibleOnOldTickets() {
        TicketResponse created = create();
        jdbc.update("update category set active = false where id = ?", category.getId());
        entityManager.clear();
        assertThat(tickets.findById(owner.getId(), created.id()).category().active()).isFalse();
    }

    @Test
    void filtersNeverExposeAnotherCollaboratorsTickets() {
        TicketResponse own = create();
        TicketResponse hidden = tickets.create(other.getId(), "Autre ticket", "Description autre ticket", category.getId());
        assertThat(tickets.findAll(owner.getId(), null, null)).extracting(TicketResponse::id).containsExactly(own.id());
        assertThat(tickets.findAll(owner.getId(), "OUVERTE", category.getId()))
                .extracting(TicketResponse::id).containsExactly(own.id());
        assertThat(tickets.findAll(owner.getId(), "RESOLUE", category.getId())).isEmpty();
        assertThat(tickets.findAll(technician.getId(), null, category.getId()))
                .extracting(TicketResponse::id).containsExactlyInAnyOrder(own.id(), hidden.id());
        assertThat(tickets.findAll(technician.getId(), "OUVERTE", category.getId())).hasSize(2);
        expectError(INVALID_INPUT, () -> tickets.findAll(owner.getId(), "INCONNU", null));
        expectError(INVALID_INPUT, () -> tickets.findAll(owner.getId(), null, -1L));
    }

    @Test
    void hidesTicketAndCommentsFromOtherCollaborator() {
        TicketResponse ticket = resolved();
        expectError(NOT_FOUND, () -> tickets.findById(other.getId(), ticket.id()));
        expectError(NOT_FOUND, () -> tickets.findById(other.getId(), Long.MAX_VALUE));
        expectError(NOT_FOUND, () -> comments.findByTicketId(other.getId(), ticket.id()));
        expectError(NOT_FOUND, () -> comments.add(other.getId(), ticket.id(), "Commentaire interdit"));
        expectError(NOT_FOUND, () -> tickets.reopen(other.getId(), ticket.id(), "Ce problème est toujours présent."));
    }

    @Test
    void collaboratorCannotPerformTechnicianActions() {
        TicketResponse ticket = create();
        expectError(FORBIDDEN, () -> tickets.assign(owner.getId(), ticket.id(), technician.getId()));
        expectError(FORBIDDEN, () -> tickets.start(owner.getId(), ticket.id()));
        expectError(FORBIDDEN, () -> tickets.resolve(owner.getId(), ticket.id(), "Une explication valide."));
        expectError(FORBIDDEN, () -> users.findActiveTechnicians(owner.getId()));
    }

    @Test
    void requiresConnectedActiveAccountAcrossServices() {
        expectError(UNAUTHENTICATED, () -> tickets.findAll(null, null, null));
        expectError(UNAUTHENTICATED, () -> categories.findActive(null));
        expectError(UNAUTHENTICATED, () -> comments.findByTicketId(null, 1L));
        expectError(UNAUTHENTICATED, () -> users.findCurrentUser(Long.MAX_VALUE));
        jdbc.update("update app_user set active = false where id = ?", owner.getId());
        entityManager.clear();
        expectError(FORBIDDEN, () -> tickets.findAll(owner.getId(), null, null));
        expectError(FORBIDDEN, () -> users.findCurrentUser(owner.getId()));
    }

    @Test
    void activeTechniciansAndCurrentUserAreReturnedWithoutPasswordField() {
        jdbc.update("update app_user set active = false where id = ?", anotherTechnician.getId());
        entityManager.clear();
        assertThat(users.findActiveTechnicians(technician.getId())).extracting(u -> u.id())
                .contains(technician.getId()).doesNotContain(owner.getId(), anotherTechnician.getId());
        assertThat(users.findCurrentUser(owner.getId()).email()).isEqualTo(owner.getEmail());
        assertThat(fr.armand.backend.dto.UserResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName).doesNotContain("passwordHash");
    }

    @Test
    void assignmentRequiresExistingActiveTechnician() {
        TicketResponse ticket = create();
        expectError(NOT_FOUND, () -> tickets.assign(technician.getId(), ticket.id(), Long.MAX_VALUE));
        expectError(INVALID_INPUT, () -> tickets.assign(technician.getId(), ticket.id(), owner.getId()));
        jdbc.update("update app_user set active = false where id = ?", anotherTechnician.getId());
        entityManager.clear();
        expectError(INVALID_INPUT, () -> tickets.assign(technician.getId(), ticket.id(), anotherTechnician.getId()));
    }

    @Test
    void assignmentDoesNotStartTicketAndInProgressTicketCannotLoseAssignee() {
        TicketResponse ticket = create();
        assertThat(tickets.assign(technician.getId(), ticket.id(), technician.getId()).status()).isEqualTo("OUVERTE");
        assertThat(tickets.assign(technician.getId(), ticket.id(), null).assignee()).isNull();
        expectError(CONFLICT, () -> tickets.start(technician.getId(), ticket.id()));
        tickets.assign(technician.getId(), ticket.id(), technician.getId());
        tickets.start(anotherTechnician.getId(), ticket.id());
        expectError(CONFLICT, () -> tickets.assign(technician.getId(), ticket.id(), null));
        assertThat(tickets.assign(technician.getId(), ticket.id(), anotherTechnician.getId()).assignee().id())
                .isEqualTo(anotherTechnician.getId());
    }

    @Test
    void refusesInvalidTransitions() {
        TicketResponse ticket = create();
        expectError(CONFLICT, () -> tickets.resolve(technician.getId(), ticket.id(), "Explication valide."));
        expectError(CONFLICT, () -> tickets.reopen(owner.getId(), ticket.id(), "Motif de réouverture valide."));
        tickets.assign(technician.getId(), ticket.id(), technician.getId());
        tickets.start(technician.getId(), ticket.id());
        expectError(CONFLICT, () -> tickets.start(technician.getId(), ticket.id()));
        expectError(CONFLICT, () -> tickets.reopen(owner.getId(), ticket.id(), "Motif de réouverture valide."));
        tickets.resolve(technician.getId(), ticket.id(), "Explication valide.");
        expectError(CONFLICT, () -> tickets.start(technician.getId(), ticket.id()));
        expectError(CONFLICT, () -> tickets.resolve(technician.getId(), ticket.id(), "Explication valide."));
        expectError(CONFLICT, () -> tickets.assign(technician.getId(), ticket.id(), anotherTechnician.getId()));
    }

    @Test
    void assignedTechnicianMustStillBeActiveWhenStartingAndResolving() {
        TicketResponse ticket = create();
        tickets.assign(technician.getId(), ticket.id(), anotherTechnician.getId());
        jdbc.update("update app_user set active = false where id = ?", anotherTechnician.getId());
        reload();
        expectError(CONFLICT, () -> tickets.start(technician.getId(), ticket.id()));
        tickets.assign(technician.getId(), ticket.id(), technician.getId());
        tickets.start(technician.getId(), ticket.id());
        jdbc.update("update app_user set active = true where id = ?", anotherTechnician.getId());
        reload();
        tickets.assign(technician.getId(), ticket.id(), anotherTechnician.getId());
        jdbc.update("update app_user set active = false where id = ?", anotherTechnician.getId());
        reload();
        expectError(CONFLICT, () -> tickets.resolve(technician.getId(), ticket.id(), "Explication valide."));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "court"})
    void rejectsBlankAndShortResolutionsAndReopeningReasons(String text) {
        TicketResponse ticket = create();
        tickets.assign(technician.getId(), ticket.id(), technician.getId());
        tickets.start(technician.getId(), ticket.id());
        expectError(INVALID_INPUT, () -> tickets.resolve(technician.getId(), ticket.id(), text));
        tickets.resolve(technician.getId(), ticket.id(), "Explication valide.");
        expectError(INVALID_INPUT, () -> tickets.reopen(owner.getId(), ticket.id(), text));
    }

    @Test
    void rejectsExcessivelyLongResolutionsAndReasons() {
        TicketResponse ticket = create();
        tickets.assign(technician.getId(), ticket.id(), technician.getId());
        tickets.start(technician.getId(), ticket.id());
        expectError(INVALID_INPUT, () -> tickets.resolve(technician.getId(), ticket.id(), "a".repeat(5001)));
        tickets.resolve(technician.getId(), ticket.id(), "Explication valide.");
        expectError(INVALID_INPUT, () -> tickets.reopen(owner.getId(), ticket.id(), "a".repeat(5001)));
    }

    @Test
    void reopeningArchivesPreviousResolutionAndClearsCurrentResolution() {
        TicketResponse resolved = resolved();
        String reason = "Le problème est toujours présent.";
        tickets.reopen(owner.getId(), resolved.id(), reason);
        reload();
        TicketResponse reopened = tickets.findById(owner.getId(), resolved.id());
        assertThat(reopened.status()).isEqualTo("OUVERTE");
        assertThat(reopened.assignee()).isNull();
        assertThat(reopened.resolution()).isNull();
        assertThat(reopened.resolvedAt()).isNull();
        var history = comments.findByTicketId(owner.getId(), resolved.id());
        assertThat(history).extracting(c -> c.kind()).containsExactly("RESOLUTION_ARCHIVEE", "REOUVERTURE");
        assertThat(history.get(0).content()).contains(resolved.resolution(), resolved.resolvedAt().toString());
        assertThat(history.get(1).content()).isEqualTo(reason);
        assertThat(history).allSatisfy(c -> assertThat(c.author().id()).isEqualTo(owner.getId()));
        // Un technicien peut aussi rouvrir le ticket d'un autre compte.
        tickets.assign(technician.getId(), resolved.id(), technician.getId());
        tickets.start(technician.getId(), resolved.id());
        tickets.resolve(technician.getId(), resolved.id(), "Deuxième résolution valide.");
        tickets.reopen(anotherTechnician.getId(), resolved.id(), "Deuxième réouverture justifiée.");
        assertThat(comments.findByTicketId(owner.getId(), resolved.id())).hasSize(4);
    }

    @Test
    void commentsRemainPossibleAfterResolutionAndUpdateTicketTimestamp() {
        TicketResponse resolved = resolved();
        reload();
        // Date ancienne explicite pour vérifier la mise à jour sans temporisation du test.
        Instant oldDate = Instant.parse("2020-01-01T00:00:00Z");
        jdbc.update("update ticket set updated_at = ? where id = ?", java.sql.Timestamp.from(oldDate), resolved.id());
        entityManager.clear();
        var comment = comments.add(owner.getId(), resolved.id(), "  Merci pour votre intervention.  ");
        comments.add(anotherTechnician.getId(), resolved.id(), "Avec plaisir.");
        reload();
        assertThat(comment.kind()).isEqualTo("COMMENTAIRE");
        assertThat(comment.author().id()).isEqualTo(owner.getId());
        assertThat(comment.content()).isEqualTo("Merci pour votre intervention.");
        assertThat(comments.findByTicketId(owner.getId(), resolved.id())).hasSize(2);
        TicketResponse stored = tickets.findById(owner.getId(), resolved.id());
        assertThat(stored.status()).isEqualTo("RESOLUE");
        assertThat(stored.resolution()).isEqualTo(resolved.resolution());
        assertThat(stored.updatedAt()).isAfter(oldDate);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t\n "})
    void rejectsEmptyComments(String content) {
        TicketResponse ticket = create();
        expectError(INVALID_INPUT, () -> comments.add(owner.getId(), ticket.id(), content));
    }

    @Test
    void checksCommentLengthBoundaries() {
        TicketResponse ticket = create();
        assertThat(comments.add(owner.getId(), ticket.id(), "a").id()).isNotNull();
        assertThat(comments.add(owner.getId(), ticket.id(), "a".repeat(5000)).id()).isNotNull();
        expectError(INVALID_INPUT, () -> comments.add(owner.getId(), ticket.id(), "a".repeat(5001)));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reopeningFailureRollsBackAlreadyInsertedCommentsAndTicketChanges() {
        // Ce test laisse le service gérer ses propres transactions pour observer un vrai rollback.
        TicketResponse resolved = resolved();
        TicketResponse beforeFailure = tickets.findById(owner.getId(), resolved.id());
        try {
            doAnswer(invocation -> {
                Iterable<TicketComment> history = invocation.getArgument(0);
                history.forEach(entityManager::persist);
                entityManager.flush();
                throw new RuntimeException("Échec simulé après insertion des commentaires");
            }).when(commentRepository).saveAll(any());
            assertThatThrownBy(() -> tickets.reopen(owner.getId(), resolved.id(), "Le problème est toujours présent."))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Échec simulé après insertion des commentaires");
            TicketResponse stored = tickets.findById(owner.getId(), resolved.id());
            assertThat(stored.status()).isEqualTo("RESOLUE");
            assertThat(stored.assignee().id()).isEqualTo(technician.getId());
            assertThat(stored.resolution()).isEqualTo(resolved.resolution());
            assertThat(stored.updatedAt()).isEqualTo(beforeFailure.updatedAt());
            assertThat(comments.findByTicketId(owner.getId(), resolved.id())).isEmpty();
        } finally {
            reset(commentRepository);
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                List<TicketComment> createdComments = commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(resolved.id());
                commentRepository.deleteAll(createdComments);
                commentRepository.flush();
                ticketRepository.deleteById(resolved.id());
                ticketRepository.flush();
                categoryRepository.deleteById(category.getId());
                userRepository.deleteAllById(List.of(owner.getId(), other.getId(), technician.getId(), anotherTechnician.getId()));
            });
        }
    }
}
