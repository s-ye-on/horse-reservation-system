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
class ReservationSummaryRepositoryIntegrationTest {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 예약_집계용_일자_상태_인덱스를_순서대로_생성한다() {
		final String indexedColumns = jdbcTemplate.queryForObject("""
			SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = 'reservations'
			  AND index_name = 'idx_reservations_lesson_status'
			""", String.class);

		assertThat(indexedColumns).isEqualTo("lesson_date,status");
	}
}
