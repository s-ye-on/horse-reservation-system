package com.horse.reservations.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.RidingClass;
import com.horse.reservations.domain.exception.ReservationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "reservations")
public class Reservation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Enumerated(EnumType.STRING)
	@Column(name = "class_type", nullable = false)
	private RidingClass ridingClass;

	@Column(name = "lesson_date", nullable = false)
	private LocalDate lessonDate;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "status", nullable = false)
	private ReservationStatus status;

	@Column(name = "payment_source", nullable = false)
	private PaymentSource paymentSource;

	@Column(name = "coupon_id")
	private Long couponId;

	@Column(name = "payment_due_at")
	private LocalDateTime paymentDueAt;

	@Column(name = "approval_requested_at", nullable = false)
	private LocalDateTime approvalRequestedAt;

	@Column(name = "admin_confirmed_at")
	private LocalDateTime adminConfirmedAt;

	@Column(name = "rejected_at")
	private LocalDateTime rejectedAt;

	@Column(name = "rejected_by")
	private String rejectedBy;

	@Column(name = "rejection_reason")
	private String rejectionReason;

	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;

	@Column(name = "cancellation_responsibility")
	private CancellationResponsibility cancellationResponsibility;

	@Column(name = "coupon_action")
	private CouponAction couponAction;

	@Column(name = "admin_memo")
	private String adminMemo;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	protected Reservation() {
	}

	private Reservation(
		Long memberId,
		RidingClass ridingClass,
		LocalDate lessonDate,
		LocalTime startTime,
		ReservationStatus status,
		PaymentSource paymentSource,
		Long couponId,
		LocalDateTime paymentDueAt,
		LocalDateTime approvalRequestedAt
	) {
		this.memberId = requireMemberId(memberId);
		this.ridingClass = requireRidingClass(ridingClass);
		this.lessonDate = requireLessonDate(lessonDate);
		this.startTime = requireStartTime(startTime);
		this.status = status;
		this.paymentSource = paymentSource;
		this.couponId = couponId;
		this.paymentDueAt = paymentDueAt;
		this.approvalRequestedAt = requireApprovalRequestedAt(approvalRequestedAt);
	}

	public static Reservation createCouponPending(
		Long memberId,
		RidingClass ridingClass,
		LocalDate lessonDate,
		LocalTime startTime,
		Long couponId,
		LocalDateTime approvalRequestedAt
	) {
		if (couponId == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_COUPON_ID);
		}
		return new Reservation(
			memberId,
			ridingClass,
			lessonDate,
			startTime,
			ReservationStatus.PENDING_ADMIN_APPROVAL,
			PaymentSource.COUPON,
			couponId,
			null,
			approvalRequestedAt);
	}

	public static Reservation createSinglePaymentPending(
		Long memberId,
		RidingClass ridingClass,
		LocalDate lessonDate,
		LocalTime startTime,
		LocalDateTime paymentDueAt,
		LocalDateTime approvalRequestedAt
	) {
		if (paymentDueAt == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_PAYMENT_DUE_AT);
		}
		return new Reservation(
			memberId,
			ridingClass,
			lessonDate,
			startTime,
			ReservationStatus.PENDING_PAYMENT,
			PaymentSource.SINGLE_PAYMENT,
			null,
			paymentDueAt,
			approvalRequestedAt);
	}

	public boolean confirm(LocalDateTime confirmedAt) {
		if (confirmedAt == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_APPROVAL_REQUESTED_AT);
		}
		if (status == ReservationStatus.CONFIRMED) {
			return false;
		}
		if (status != ReservationStatus.PENDING_ADMIN_APPROVAL
			&& status != ReservationStatus.PENDING_PAYMENT) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_STATUS);
		}
		if (status == ReservationStatus.PENDING_PAYMENT && !confirmedAt.isBefore(paymentDueAt)) {
			throw new ReservationException(ExceptionCode.RESERVATION_PAYMENT_EXPIRED);
		}
		status = ReservationStatus.CONFIRMED;
		adminConfirmedAt = confirmedAt;
		return true;
	}

	private static Long requireMemberId(Long memberId) {
		if (memberId == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_MEMBER_ID);
		}
		return memberId;
	}

	private static RidingClass requireRidingClass(RidingClass ridingClass) {
		if (ridingClass == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_RIDING_CLASS);
		}
		return ridingClass;
	}

	private static LocalDate requireLessonDate(LocalDate lessonDate) {
		if (lessonDate == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_LESSON_DATE);
		}
		return lessonDate;
	}

	private static LocalTime requireStartTime(LocalTime startTime) {
		if (startTime == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_START_TIME);
		}
		return startTime;
	}

	private static LocalDateTime requireApprovalRequestedAt(LocalDateTime approvalRequestedAt) {
		if (approvalRequestedAt == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_APPROVAL_REQUESTED_AT);
		}
		return approvalRequestedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getMemberId() {
		return memberId;
	}

	public RidingClass getRidingClass() {
		return ridingClass;
	}

	public LocalDate getLessonDate() {
		return lessonDate;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public ReservationStatus getStatus() {
		return status;
	}

	public PaymentSource getPaymentSource() {
		return paymentSource;
	}

	public Long getCouponId() {
		return couponId;
	}

	public LocalDateTime getPaymentDueAt() {
		return paymentDueAt;
	}

	public LocalDateTime getApprovalRequestedAt() {
		return approvalRequestedAt;
	}

	public LocalDateTime getAdminConfirmedAt() {
		return adminConfirmedAt;
	}

	public long getVersion() {
		return version;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
