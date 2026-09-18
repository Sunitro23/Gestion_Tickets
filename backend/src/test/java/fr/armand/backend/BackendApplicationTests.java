package fr.armand.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class BackendApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void connectsToPostgresql() {
		assertThat(jdbcTemplate.queryForObject("SELECT version()", String.class))
				.startsWith("PostgreSQL ");
	}

}
