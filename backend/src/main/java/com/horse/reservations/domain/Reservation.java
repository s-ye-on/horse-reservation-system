package com.horse.reservations.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

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
	private static final int MAX_REJECTION_REASON_LENGTH = 500;
	private static final int MAX_ADMIN_MEMO_LENGTH = 500;

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

	public boolean reject(LocalDateTime occurredAt, String adminSubject, String reason) {
		if (status == ReservationStatus.REJECTED) {
			return false;
		}
		if (status != ReservationStatus.PENDING_ADMIN_APPROVAL
			&& status != ReservationStatus.PENDING_PAYMENT) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_STATUS);
		}
		if (occurredAt == null || adminSubject == null || adminSubject.isBlank()) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_REJECTION_ACTOR);
		}
		if (reason == null || reason.isBlank() || reason.length() > MAX_REJECTION_REASON_LENGTH) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_REJECTION_REASON);
		}
		status = ReservationStatus.REJECTED;
		rejectedAt = occurredAt;
		rejectedBy = adminSubject;
		rejectionReason = reason;
		return true;
	}

	public boolean expirePayment(LocalDateTime expiredAt) {
		if (expiredAt == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_PAYMENT_EXPIRY_AT);
		}
		if (status != ReservationStatus.PENDING_PAYMENT) {
			return false;
		}
		if (paymentDueAt == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_PERSISTED_VALUE);
		}
		if (expiredAt.isBefore(paymentDueAt)) {
			return false;
		}
		status = ReservationStatus.PAYMENT_EXPIRED;
		return true;
	}

	public void restorePayment(LocalDateTime restoredAt) {
		if (restoredAt == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_PAYMENT_RESTORE_AT);
		}
		ensurePaymentRestorable();
		status = ReservationStatus.CONFIRMED;
		adminConfirmedAt = restoredAt;
	}

	public void ensurePaymentRestorable() {
		if (status != ReservationStatus.PAYMENT_EXPIRED || paymentSource != PaymentSource.SINGLE_PAYMENT) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_STATUS);
		}
	}

	public boolean completeRide() {
		if (status == ReservationStatus.COMPLETED) {
			return false;
		}
		if (status != ReservationStatus.CONFIRMED) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_STATUS);
		}
		status = ReservationStatus.COMPLETED;
		return true;
	}

	public boolean changeSchedule(LocalDate targetLessonDate, LocalTime targetStartTime) {
		final LocalDate validatedLessonDate = requireLessonDate(targetLessonDate);
		final LocalTime validatedStartTime = requireStartTime(targetStartTime);
		ensureChangeable();
		if (lessonDate.equals(validatedLessonDate) && startTime.equals(validatedStartTime)) {
			return false;
		}
		lessonDate = validatedLessonDate;
		startTime = validatedStartTime;
		return true;
	}

	public void ensureSchedule(LocalDate expectedLessonDate, LocalTime expectedStartTime) {
		final LocalDate validatedLessonDate = requireLessonDate(expectedLessonDate);
		final LocalTime validatedStartTime = requireStartTime(expectedStartTime);
		if (!lessonDate.equals(validatedLessonDate) || !startTime.equals(validatedStartTime)) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_STATUS);
		}
	}

	public void ensureChangeable() {
		if (!status.occupiesCapacity()) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_STATUS);
		}
	}

	public boolean hasSchedule(LocalDate expectedLessonDate, LocalTime expectedStartTime) {
		return lessonDate.equals(requireLessonDate(expectedLessonDate))
			&& startTime.equals(requireStartTime(expectedStartTime));
	}

	public boolean recordNoShow(CouponAction requestedCouponAction, String memo) {
		final CouponAction validatedCouponAction = requireNoShowCouponAction(requestedCouponAction);
		final String validatedMemo = requireAdminMemo(memo);
		if (status == ReservationStatus.NO_SHOW) {
			if (couponAction == validatedCouponAction && Objects.equals(adminMemo, validatedMemo)) {
				return false;
			}
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_STATUS);
		}
		if (status != ReservationStatus.CONFIRMED) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_STATUS);
		}
		status = ReservationStatus.NO_SHOW;
		couponAction = validatedCouponAction;
		adminMemo = validatedMemo;
		return true;
	}

	private CouponAction requireNoShowCouponAction(CouponAction requestedCouponAction) {
		if (requestedCouponAction == null || requestedCouponAction == CouponAction.FREE_CHANGE_USED) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_COUPON_ACTION);
		}
		if (paymentSource == PaymentSource.COUPON && requestedCouponAction == CouponAction.NONE) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_COUPON_ACTION);
		}
		if (paymentSource == PaymentSource.SINGLE_PAYMENT && requestedCouponAction != CouponAction.NONE) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_COUPON_ACTION);
		}
		return requestedCouponAction;
	}

	private String requireAdminMemo(String memo) {
		if (memo == null || memo.isBlank() || memo.length() > MAX_ADMIN_MEMO_LENGTH) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_ADMIN_MEMO);
		}
		return memo;
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

	public LocalDateTime getRejectedAt() {
		return rejectedAt;
	}

	public String getRejectedBy() {
		return rejectedBy;
	}

	public String getRejectionReason() {
		return rejectionReason;
	}

	public LocalDateTime getCancelledAt() {
		return cancelledAt;
	}

	public CancellationResponsibility getCancellationResponsibility() {
		return cancellationResponsibility;
	}

	public CouponAction getCouponAction() {
		return couponAction;
	}

	public String getAdminMemo() {
		return adminMemo;
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
