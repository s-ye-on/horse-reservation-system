package com.horse.reservations.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.horse.TestcontainersConfiguration;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationStatus;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class MemberReservationFilterRepositoryIntegrationTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);
	private static final LocalTime CURRENT_TIME = LocalTime.of(10, 0);

	@Autowired
	ReservationRepository reservationRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 예정과_지난_예약은_상태와_무관하게_경계와_정렬을_지킨다() {
		final Long memberId = insertMember("member-filter-boundary");
		final Long exactStartId = insertReservation(
			memberId, TODAY, "10:00:00", "confirmed");
		final Long pastId = insertReservation(memberId, TODAY, "09:59:59", "no_show");
		final Long firstUpcomingId = insertReservation(memberId, TODAY, "10:00:01", "completed");
		final Long sameStartUpcomingId = insertReservation(
			memberId, TODAY, "10:00:01", "completed");
		final Long secondUpcomingId = insertReservation(
			memberId, TODAY.plusDays(1), "09:00:00", "cancelled");

		final Page<Reservation> upcoming = reservationRepository.findUpcomingMemberReservations(
			memberId,
			null,
			TODAY,
			CURRENT_TIME,
			PageRequest.of(0, 20));
		final Page<Reservation> past = reservationRepository.findPastMemberReservations(
			memberId,
			null,
			TODAY,
			CURRENT_TIME,
			PageRequest.of(0, 20));

		assertThat(upcoming.getContent())
			.extracting(Reservation::getId)
			.containsExactly(firstUpcomingId, sameStartUpcomingId, secondUpcomingId);
		assertThat(upcoming.getTotalElements()).isEqualTo(3);
		assertThat(past.getContent())
			.extracting(Reservation::getId)
			.containsExactly(exactStartId, pastId);
		assertThat(past.getTotalElements()).isEqualTo(2);
	}

	@Test
	void 표시_그룹과_상태_필터의_count와_Page_메타데이터를_DB에서_계산한다() {
		final Long memberId = insertMember("member-filter-page");
		final Long firstId = insertReservation(memberId, TODAY, "10:00:01", "confirmed");
		final Long secondId = insertReservation(
			memberId, TODAY.plusDays(1), "09:00:00", "confirmed");
		insertReservation(memberId, TODAY.plusDays(2), "09:00:00", "completed");
		insertReservation(insertMember("member-filter-other"), TODAY, "10:00:01", "confirmed");

		final Page<Reservation> firstPage = reservationRepository.findUpcomingMemberReservations(
			memberId,
			ReservationStatus.CONFIRMED,
			TODAY,
			CURRENT_TIME,
			PageRequest.of(0, 1));
		final Page<Reservation> secondPage = reservationRepository.findUpcomingMemberReservations(
			memberId,
			ReservationStatus.CONFIRMED,
			TODAY,
			CURRENT_TIME,
			PageRequest.of(1, 1));

		assertThat(firstPage.getContent())
			.extracting(Reservation::getId)
			.containsExactly(firstId);
		assertThat(firstPage.getTotalElements()).isEqualTo(2);
		assertThat(firstPage.getTotalPages()).isEqualTo(2);
		assertThat(firstPage.hasNext()).isTrue();
		assertThat(secondPage.getContent())
			.extracting(Reservation::getId)
			.containsExactly(secondId);
		assertThat(secondPage.hasNext()).isFalse();
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES (?, '회원 예약 필터', '010-0000-0000')
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertReservation(
		Long memberId,
		LocalDate lessonDate,
		String startTime,
		String status
	) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, end_time, status,
				payment_source, payment_due_at, approval_requested_at, admin_confirmed_at,
				coupon_action, admin_memo, cancelled_at, cancellation_responsibility
			) VALUES (
				?, 'FIRST_RIDE', ?, ?, ADDTIME(?, '00:45:00'), ?,
				'single_payment', '2026-07-15 12:00:00', '2026-07-15 09:00:00',
				IF(? IN ('confirmed', 'completed', 'no_show'), '2026-07-15 09:10:00', NULL),
				IF(? = 'no_show', 'none', NULL),
				IF(? = 'no_show', '노쇼 처리', NULL),
				IF(? = 'cancelled', '2026-07-15 09:20:00', NULL),
				IF(? = 'cancelled', 'member', NULL)
			)
			""",
			memberId,
			lessonDate,
			startTime,
			startTime,
			status,
			status,
			status,
			status,
			status,
			status);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}
}
