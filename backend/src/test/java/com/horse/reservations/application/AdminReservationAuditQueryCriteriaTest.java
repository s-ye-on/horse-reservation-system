package com.horse.reservations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.ReservationActorType;
import com.horse.reservations.domain.ReservationChangeType;
import com.horse.reservations.domain.exception.ReservationException;

class AdminReservationAuditQueryCriteriaTest {

	@Test
	void 생략한_조건은_전체_기간과_기본_페이지를_사용한다() {
		final AdminReservationAuditQueryCriteria criteria = AdminReservationAuditQueryCriteria.create(
			"  ", null, null, null, null, null, null, null);

		assertThat(criteria.keyword()).isNull();
		assertThat(criteria.occurredAtFrom()).isNull();
		assertThat(criteria.occurredAtTo()).isNull();
		assertThat(criteria.page()).isZero();
		assertThat(criteria.size()).isEqualTo(20);
	}

	@Test
	void 문자열_필터와_기간을_정규화한다() {
		final AdminReservationAuditQueryCriteria criteria = AdminReservationAuditQueryCriteria.create(
			"  김하늘  ",
			12L,
			LocalDate.of(2026, 7, 1),
			LocalDate.of(2026, 7, 31),
			"ADMIN",
			"schedule_changed",
			1,
			50);

		assertThat(criteria.keyword()).isEqualTo("김하늘");
		assertThat(criteria.actorType()).isEqualTo(ReservationActorType.ADMIN);
		assertThat(criteria.changeType()).isEqualTo(ReservationChangeType.SCHEDULE_CHANGED);
		assertThat(criteria.occurredAtFrom()).isEqualTo("2026-07-01T00:00:00");
		assertThat(criteria.occurredAtTo().toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 31));
		assertThat(criteria.page()).isEqualTo(1);
		assertThat(criteria.size()).isEqualTo(50);
	}

	@Test
	void 잘못된_필터와_기간과_페이지를_거부한다() {
		assertExceptionCode(
			() -> AdminReservationAuditQueryCriteria.create(
				null, null, null, null, "unknown", null, null, null),
			ExceptionCode.RESERVATION_INVALID_AUDIT_ACTOR_TYPE);
		assertExceptionCode(
			() -> AdminReservationAuditQueryCriteria.create(
				null, null, null, null, null, "unknown", null, null),
			ExceptionCode.RESERVATION_INVALID_AUDIT_CHANGE_TYPE);
		assertExceptionCode(
			() -> AdminReservationAuditQueryCriteria.create(
				null,
				null,
				LocalDate.of(2026, 7, 2),
				LocalDate.of(2026, 7, 1),
				null,
				null,
				null,
				null),
			ExceptionCode.RESERVATION_INVALID_AUDIT_DATE_RANGE);
		assertExceptionCode(
			() -> AdminReservationAuditQueryCriteria.create(
				null, null, null, null, null, null, -1, 20),
			ExceptionCode.RESERVATION_INVALID_AUDIT_PAGE);
	}

	private void assertExceptionCode(Runnable action, ExceptionCode exceptionCode) {
		assertThatThrownBy(action::run)
			.isInstanceOf(ReservationException.class)
			.hasMessage(exceptionCode.message());
	}
}
