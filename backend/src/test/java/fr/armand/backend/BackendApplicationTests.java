package fr.armand.backend;

import static org.assertj.core.api.Assertions.assertThat;

import fr.armand.backend.entity.Category;
import fr.armand.backend.entity.AppUser;
import fr.armand.backend.entity.Ticket;
import fr.armand.backend.entity.TicketComment;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class BackendApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Test
	void connectsToPostgresql() {
		assertThat(jdbcTemplate.queryForObject("SELECT version()", String.class))
				.startsWith("PostgreSQL ");
	}

	@Test
	@Transactional
	void savesAndReadsCategory() {
		Category category = new Category("Test-" + java.util.UUID.randomUUID());
		entityManager.persist(category);
		entityManager.flush();
		entityManager.clear();

		Category savedCategory = entityManager.find(Category.class, category.getId());
		assertThat(category.getId()).isNotNull();
		assertThat(savedCategory).isNotNull();
		assertThat(savedCategory.getName()).isEqualTo(category.getName());
		assertThat(savedCategory.isActive()).isTrue();
	}

	@Test
	@Transactional
	void savesAndReadsTicketWithRequesterCategoryAndComment() {
		String uniqueName = "Test-" + java.util.UUID.randomUUID();
		AppUser requester = new AppUser("Camille", uniqueName + "@example.test",
				"test-hash", "COLLABORATEUR");
		AppUser technician = new AppUser("Camille", uniqueName + "-tech@example.test",
				"test-hash", "TECHNICIEN");
		Category category = new Category(uniqueName);
		entityManager.persist(requester);
		entityManager.persist(technician);
		entityManager.persist(category);

		Ticket ticket = new Ticket("Imprimante inaccessible", "Impossible de lancer une impression.",
				requester, category);
		entityManager.persist(ticket);
		TicketComment comment = new TicketComment(ticket, technician, "Je regarde le problème.");
		entityManager.persist(comment);
		entityManager.flush();
		entityManager.clear();

		TicketComment savedComment = entityManager.find(TicketComment.class, comment.getId());
		assertThat(savedComment.getContent()).isEqualTo(comment.getContent());
		assertThat(savedComment.getKind()).isEqualTo("COMMENTAIRE");
		assertThat(savedComment.getCreatedAt()).isNotNull();
		assertThat(savedComment.getAuthor().getId()).isEqualTo(technician.getId());
		assertThat(savedComment.getAuthor().getRole()).isEqualTo("TECHNICIEN");
		Ticket savedTicket = savedComment.getTicket();
		assertThat(savedTicket.getId()).isEqualTo(ticket.getId());
		assertThat(savedTicket.getTitle()).isEqualTo(ticket.getTitle());
		assertThat(savedTicket.getDescription()).isEqualTo(ticket.getDescription());
		assertThat(savedTicket.getCategory().getId()).isEqualTo(category.getId());
		assertThat(savedTicket.getRequester().getId()).isEqualTo(requester.getId());
		assertThat(savedTicket.getRequester().getEmail()).isEqualTo(requester.getEmail());
		assertThat(savedTicket.getRequester().isActive()).isTrue();
		assertThat(savedTicket.getRequester().getCreatedAt()).isNotNull();
		assertThat(savedTicket.getStatus()).isEqualTo("OUVERTE");
		assertThat(savedTicket.getAssignee()).isNull();
		assertThat(savedTicket.getResolution()).isNull();
		assertThat(savedTicket.getResolvedAt()).isNull();
		assertThat(savedTicket.getCreatedAt()).isNotNull().isEqualTo(savedTicket.getUpdatedAt());
	}
}
