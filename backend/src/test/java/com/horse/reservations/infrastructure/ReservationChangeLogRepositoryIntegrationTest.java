package com.horse.reservations.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import com.horse.TestcontainersConfiguration;
import com.horse.reservations.domain.CouponAction;
import com.horse.reservations.domain.ReservationActorType;
import com.horse.reservations.domain.ReservationChangeLog;
import com.horse.reservations.domain.ReservationChangeType;
import com.horse.reservations.domain.ReservationStatus;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class ReservationChangeLogRepositoryIntegrationTest {

	@Autowired
	ReservationChangeLogRepository repository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	EntityManager entityManager;

	@Test
	void 복구_행위를_추가하고_예약별_발생순으로_조회한다() {
		final Long reservationId = insertExpiredReservation("change-log-member");
		final ReservationChangeLog changeLog = ReservationChangeLog.paymentRestored(
			reservationId,
			"restore-admin",
			LocalDate.of(2026, 8, 1),
			LocalTime.of(9, 0),
			"입금을 확인해 예약을 복구함");

		repository.save(changeLog);
		entityManager.flush();
		entityManager.clear();

		final ReservationChangeLog saved = repository
			.findAllByReservationIdOrderByCreatedAtAscIdAsc(reservationId)
			.getFirst();
		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getActorAuthSubject()).isEqualTo("restore-admin");
		assertThat(saved.getActorType()).isEqualTo(ReservationActorType.ADMIN);
		assertThat(saved.getFromStatus()).isEqualTo(ReservationStatus.PAYMENT_EXPIRED);
		assertThat(saved.getToStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(saved.getChangeType()).isEqualTo(ReservationChangeType.PAYMENT_RESTORED);
		assertThat(saved.getCouponAction()).isEqualTo(CouponAction.NONE);
		assertThat(saved.getMemo()).isEqualTo("입금을 확인해 예약을 복구함");
		assertThat(saved.getCreatedAt()).isNotNull();
	}

	@Test
	void 일정_변경_행위를_추가하고_전후_일정과_메모를_보존한다() {
		final Long reservationId = insertActiveReservation("schedule-change-member");
		final ReservationChangeLog changeLog = ReservationChangeLog.reservationChanged(
			reservationId,
			"change-admin",
			ReservationActorType.ADMIN,
			ReservationStatus.CONFIRMED,
			LocalDate.of(2026, 8, 1),
			LocalTime.of(9, 0),
			LocalDate.of(2026, 8, 2),
			LocalTime.of(10, 0),
			"우천으로 일정 조정");

		repository.save(changeLog);
		entityManager.flush();
		entityManager.clear();

		final ReservationChangeLog saved = repository
			.findAllByReservationIdOrderByCreatedAtAscIdAsc(reservationId)
			.getFirst();
		assertThat(saved.getActorType()).isEqualTo(ReservationActorType.ADMIN);
		assertThat(saved.getFromStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(saved.getToStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(saved.getFromLessonDate()).isEqualTo(LocalDate.of(2026, 8, 1));
		assertThat(saved.getToLessonDate()).isEqualTo(LocalDate.of(2026, 8, 2));
		assertThat(saved.getChangeType()).isEqualTo(ReservationChangeType.SCHEDULE_CHANGED);
		assertThat(saved.getCouponAction()).isEqualTo(CouponAction.NONE);
		assertThat(saved.getMemo()).isEqualTo("우천으로 일정 조정");
	}

	@Test
	void 데이터베이스가_잘못된_복구_조합과_빈_메모를_거부한다() {
		final Long reservationId = insertExpiredReservation("constraint-log-member");

		assertThatThrownBy(() -> insertRawLog(
			reservationId, "admin", "confirmed", "payment_expired", "none", "복구"))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertRawLog(
			reservationId, "admin", "payment_expired", "confirmed", "none", " "))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void 데이터베이스가_잘못된_예약_변경_조합을_거부한다() {
		final Long reservationId = insertActiveReservation("invalid-schedule-member");

		assertThatThrownBy(() -> insertRawScheduleChangeLog(
			reservationId, "system", "confirmed", "confirmed", "none", "메모"))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertRawScheduleChangeLog(
			reservationId, "admin", "confirmed", "confirmed", "return", "메모"))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertRawScheduleChangeLog(
			reservationId, "admin", "confirmed", "confirmed", "none", " "))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void 변경_이력은_불변_엔티티이며_Repository가_삭제_API를_노출하지_않는다() {
		assertThat(ReservationChangeLog.class.isAnnotationPresent(Immutable.class)).isTrue();
		assertThat(JpaRepository.class.isAssignableFrom(ReservationChangeLogRepository.class)).isFalse();
		assertThat(Arrays.stream(ReservationChangeLogRepository.class.getMethods())
			.map(java.lang.reflect.Method::getName))
			.noneMatch(methodName -> methodName.startsWith("delete"));
	}

	@Test
	void 예약별_생성순_조회_인덱스를_생성한다() {
		final Integer indexColumnCount = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = 'reservation_change_logs'
			  AND index_name = 'idx_reservation_change_logs_reservation_created'
			""", Integer.class);

		assertThat(indexColumnCount).isEqualTo(3);
	}

	private Long insertExpiredReservation(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '이력 회원', '010-0000-0000', FALSE)
			""", authSubject);
		final Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', '2026-08-01', '09:00:00',
				'payment_expired', 'single_payment', '2026-07-15 10:00:00', '2026-07-15 08:00:00')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertActiveReservation(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '일정 회원', '010-0000-0000', FALSE)
			""", authSubject);
		final Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				approval_requested_at, admin_confirmed_at
			) VALUES (?, 'FIRST_RIDE', '2026-08-01', '09:00:00',
				'confirmed', 'single_payment', '2026-07-15 08:00:00', '2026-07-15 10:00:00')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertRawLog(
		Long reservationId,
		String actorType,
		String fromStatus,
		String toStatus,
		String couponAction,
		String memo
	) {
		jdbcTemplate.update("""
			INSERT INTO reservation_change_logs (
				reservation_id, actor_auth_subject, actor_type, from_status, to_status,
				from_lesson_date, from_start_time, to_lesson_date, to_start_time,
				change_type, coupon_action, memo
			) VALUES (?, 'restore-admin', ?, ?, ?, '2026-08-01', '09:00:00',
				'2026-08-01', '09:00:00', 'payment_restored', ?, ?)
			""", reservationId, actorType, fromStatus, toStatus, couponAction, memo);
	}

	private void insertRawScheduleChangeLog(
		Long reservationId,
		String actorType,
		String fromStatus,
		String toStatus,
		String couponAction,
		String memo
	) {
		jdbcTemplate.update("""
			INSERT INTO reservation_change_logs (
				reservation_id, actor_auth_subject, actor_type, from_status, to_status,
				from_lesson_date, from_start_time, to_lesson_date, to_start_time,
				change_type, coupon_action, memo
			) VALUES (?, 'change-admin', ?, ?, ?, '2026-08-01', '09:00:00',
				'2026-08-02', '10:00:00', 'schedule_changed', ?, ?)
			""", reservationId, actorType, fromStatus, toStatus, couponAction, memo);
	}
}
