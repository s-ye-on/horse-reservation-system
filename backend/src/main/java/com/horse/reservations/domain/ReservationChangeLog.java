package com.horse.reservations.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.hibernate.annotations.Immutable;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "reservation_change_logs")
public class ReservationChangeLog {

	private static final int MAX_ACTOR_AUTH_SUBJECT_LENGTH = 191;
	private static final int MAX_MEMO_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "reservation_id", nullable = false, updatable = false)
	private Long reservationId;

	@Column(name = "actor_auth_subject", nullable = false, updatable = false)
	private String actorAuthSubject;

	@Column(name = "actor_type", nullable = false, updatable = false)
	private ReservationActorType actorType;

	@Column(name = "from_status", nullable = false, updatable = false)
	private ReservationStatus fromStatus;

	@Column(name = "to_status", nullable = false, updatable = false)
	private ReservationStatus toStatus;

	@Column(name = "from_lesson_date", updatable = false)
	private LocalDate fromLessonDate;

	@Column(name = "from_start_time", updatable = false)
	private LocalTime fromStartTime;

	@Column(name = "to_lesson_date", updatable = false)
	private LocalDate toLessonDate;

	@Column(name = "to_start_time", updatable = false)
	private LocalTime toStartTime;

	@Column(name = "change_type", nullable = false, updatable = false)
	private ReservationChangeType changeType;

	@Column(name = "coupon_action", nullable = false, updatable = false)
	private CouponAction couponAction;

	@Column(name = "memo", updatable = false)
	private String memo;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	protected ReservationChangeLog() {
	}

	private ReservationChangeLog(
		Long reservationId,
		String actorAuthSubject,
		ReservationActorType actorType,
		ReservationStatus fromStatus,
		ReservationStatus toStatus,
		LocalDate fromLessonDate,
		LocalTime fromStartTime,
		LocalDate toLessonDate,
		LocalTime toStartTime,
		ReservationChangeType changeType,
		CouponAction couponAction,
		String memo
	) {
		this.reservationId = requireReservationId(reservationId);
		this.actorAuthSubject = requireActorAuthSubject(actorAuthSubject);
		this.actorType = actorType;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.fromLessonDate = requireLessonDate(fromLessonDate);
		this.fromStartTime = requireStartTime(fromStartTime);
		this.toLessonDate = requireLessonDate(toLessonDate);
		this.toStartTime = requireStartTime(toStartTime);
		this.changeType = changeType;
		this.couponAction = couponAction;
		this.memo = requireMemo(memo);
	}

	public static ReservationChangeLog paymentRestored(
		Long reservationId,
		String actorAuthSubject,
		LocalDate lessonDate,
		LocalTime startTime,
		String memo
	) {
		return new ReservationChangeLog(
			reservationId,
			actorAuthSubject,
			ReservationActorType.ADMIN,
			ReservationStatus.PAYMENT_EXPIRED,
			ReservationStatus.CONFIRMED,
			lessonDate,
			startTime,
			lessonDate,
			startTime,
			ReservationChangeType.PAYMENT_RESTORED,
			CouponAction.NONE,
			memo);
	}

	public static ReservationChangeLog noShowProcessed(
		Long reservationId,
		String actorAuthSubject,
		LocalDate lessonDate,
		LocalTime startTime,
		CouponAction couponAction,
		String memo
	) {
		return new ReservationChangeLog(
			reservationId,
			actorAuthSubject,
			ReservationActorType.ADMIN,
			ReservationStatus.CONFIRMED,
			ReservationStatus.NO_SHOW,
			lessonDate,
			startTime,
			lessonDate,
			startTime,
			ReservationChangeType.NO_SHOW_PROCESSED,
			couponAction,
			memo);
	}

	private static Long requireReservationId(Long reservationId) {
		if (reservationId == null || reservationId <= 0) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_CHANGE_LOG_REFERENCE);
		}
		return reservationId;
	}

	private static String requireActorAuthSubject(String actorAuthSubject) {
		if (actorAuthSubject == null || actorAuthSubject.isBlank()
			|| actorAuthSubject.length() > MAX_ACTOR_AUTH_SUBJECT_LENGTH) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_CHANGE_LOG_ACTOR);
		}
		return actorAuthSubject;
	}

	private static LocalDate requireLessonDate(LocalDate lessonDate) {
		if (lessonDate == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_CHANGE_LOG_REFERENCE);
		}
		return lessonDate;
	}

	private static LocalTime requireStartTime(LocalTime startTime) {
		if (startTime == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_CHANGE_LOG_REFERENCE);
		}
		return startTime;
	}

	private static String requireMemo(String memo) {
		if (memo == null || memo.isBlank() || memo.length() > MAX_MEMO_LENGTH) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_CHANGE_LOG_MEMO);
		}
		return memo;
	}

	public Long getId() {
		return id;
	}

	public Long getReservationId() {
		return reservationId;
	}

	public String getActorAuthSubject() {
		return actorAuthSubject;
	}

	public ReservationActorType getActorType() {
		return actorType;
	}

	public ReservationStatus getFromStatus() {
		return fromStatus;
	}

	public ReservationStatus getToStatus() {
		return toStatus;
	}

	public LocalDate getFromLessonDate() {
		return fromLessonDate;
	}

	public LocalTime getFromStartTime() {
		return fromStartTime;
	}

	public LocalDate getToLessonDate() {
		return toLessonDate;
	}

	public LocalTime getToStartTime() {
		return toStartTime;
	}

	public ReservationChangeType getChangeType() {
		return changeType;
	}

	public CouponAction getCouponAction() {
		return couponAction;
	}

	public String getMemo() {
		return memo;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
