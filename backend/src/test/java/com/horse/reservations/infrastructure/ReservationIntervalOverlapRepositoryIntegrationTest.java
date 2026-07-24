package com.horse.reservations.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;
import com.horse.reservations.domain.Reservation;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class ReservationIntervalOverlapRepositoryIntegrationTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 1);

	@Autowired
	ReservationRepository reservationRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 같은_회원과_날짜의_활성_반개방_구간만_잠금_조회한다() {
		final Long memberId = insertMember("overlap-query-member");
		final Long anotherMemberId = insertMember("overlap-query-another");
		final Long firstId = insertReservation(memberId, "09:00:00", "pending_payment");
		final Long secondId = insertReservation(memberId, "09:30:00", "confirmed");
		insertReservation(memberId, "09:15:00", "completed");
		insertReservation(anotherMemberId, "09:30:00", "pending_payment");
		insertReservation(memberId, "09:30:00", "pending_payment", LESSON_DATE.plusDays(1));

		final List<Reservation> overlaps = reservationRepository.findActiveOverlapsForUpdate(
			memberId,
			LESSON_DATE,
			LocalTime.of(9, 20),
			LocalTime.of(10, 5));

		assertThat(overlaps)
			.extracting(Reservation::getId)
			.containsExactly(firstId, secondId);
	}

	@Test
	void 기존_종료와_후보_시작이_같으면_겹침으로_조회하지_않는다() {
		final Long memberId = insertMember("overlap-query-boundary");
		insertReservation(memberId, "10:00:00", "pending_payment");

		final List<Reservation> overlaps = reservationRepository.findActiveOverlapsForUpdate(
			memberId,
			LESSON_DATE,
			LocalTime.of(10, 45),
			LocalTime.of(11, 30));

		assertThat(overlaps).isEmpty();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void overlap_잠금_조회는_기존_트랜잭션을_필수로_요구한다() {
		assertThatThrownBy(() -> reservationRepository.findActiveOverlapsForUpdate(
			1L,
			LESSON_DATE,
			LocalTime.of(10, 0),
			LocalTime.of(10, 45)))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES (?, '겹침 조회 회원', '010-0000-0000')
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertReservation(Long memberId, String startTime, String status) {
		return insertReservation(memberId, startTime, status, LESSON_DATE);
	}

	private Long insertReservation(
		Long memberId,
		String startTime,
		String status,
		LocalDate lessonDate
	) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, end_time, status,
				payment_source, payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (
				?, 'FIRST_RIDE', ?, ?, ADDTIME(?, '00:45:00'), ?,
				'single_payment', '2026-07-31 12:00:00', '2026-07-31 10:00:00',
				IF(? = 'confirmed' OR ? = 'completed', '2026-07-31 10:10:00', NULL)
			)
			""", memberId, lessonDate, startTime, startTime, status, status, status);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}
}
