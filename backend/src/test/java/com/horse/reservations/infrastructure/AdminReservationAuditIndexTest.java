package com.horse.reservations.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.horse.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class AdminReservationAuditIndexTest {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 감사_이력_최신순_조회_인덱스를_순서대로_생성한다() {
		final String indexedColumns = jdbcTemplate.queryForObject("""
			SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = 'reservation_change_logs'
			  AND index_name = 'idx_reservation_change_logs_created'
			""", String.class);

		assertThat(indexedColumns).isEqualTo("created_at,id");
	}
}
