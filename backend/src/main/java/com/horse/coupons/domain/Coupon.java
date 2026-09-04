package com.horse.coupons.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UpdateTimestamp;

import com.horse.coupons.domain.exception.CouponException;
import com.horse.global.exception.ExceptionCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "coupons")
public class Coupon {

	private static final int VALIDITY_MONTHS = 3;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "coupon_type", nullable = false)
	private CouponType type;

	@Column(name = "total_count", nullable = false)
	private int totalCount;

	@Column(name = "remaining_count", nullable = false)
	private int remainingCount;

	@Column(name = "held_count", nullable = false)
	private int heldCount;

	@Column(name = "first_used_at")
	private LocalDateTime firstUsedAt;

	@Column(name = "expires_at")
	private LocalDateTime expiresAt;

	@Column(name = "free_change_used", nullable = false)
	private boolean freeChangeUsed;

	@Column(name = "status", nullable = false)
	private CouponStatus status;

	@Column(name = "created_by", nullable = false, updatable = false)
	private String createdBy;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	@CreationTimestamp(source = SourceType.DB)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	@UpdateTimestamp(source = SourceType.DB)
	private LocalDateTime updatedAt;

	protected Coupon() {
	}

	private Coupon(
		Long memberId,
		CouponType type,
		Integer totalCount,
		Integer usedCount,
		LocalDate firstUsedDate,
		String createdBy
	) {
		this.memberId = requireMemberId(memberId);
		this.type = requireType(type);
		this.totalCount = requireTotalCount(totalCount);
		final int registeredUsedCount = requireUsedCount(usedCount, totalCount);
		this.remainingCount = totalCount - registeredUsedCount;
		this.heldCount = 0;
		initializeUsageDates(registeredUsedCount, firstUsedDate);
		this.freeChangeUsed = false;
		this.status = remainingCount == 0 ? CouponStatus.DEPLETED : CouponStatus.ACTIVE;
		this.createdBy = requireCreatedBy(createdBy);
	}

	public static Coupon create(Long memberId, CouponType type, Integer totalCount, String createdBy) {
		return new Coupon(memberId, type, totalCount, 0, null, createdBy);
	}

	public static Coupon register(
		Long memberId,
		CouponType type,
		Integer totalCount,
		Integer usedCount,
		LocalDate firstUsedDate,
		LocalDate registrationDate,
		String createdBy
	) {
		final Coupon coupon = new Coupon(memberId, type, totalCount, usedCount, firstUsedDate, createdBy);
		coupon.ensureImportableOn(registrationDate);
		return coupon;
	}

	public void hold(LocalDate lessonDate) {
		ensureUsableForLesson(lessonDate);
		if (status != CouponStatus.ACTIVE || heldCount >= remainingCount) {
			throw new CouponException(ExceptionCode.COUPON_HOLD_NOT_AVAILABLE);
		}
		heldCount++;
	}

	public void ensureUsableForLesson(LocalDate lessonDate) {
		if (lessonDate == null) {
			throw new CouponException(ExceptionCode.COUPON_INVALID_LESSON_DATE);
		}
		if (isPastExpiryBoundary(lessonDate)) {
			throw new CouponException(ExceptionCode.COUPON_EXPIRED_FOR_LESSON);
		}
	}

	public void releaseHold() {
		if (heldCount <= 0) {
			throw new CouponException(ExceptionCode.COUPON_HOLD_STATE_CONFLICT);
		}
		heldCount--;
		if (status == CouponStatus.EXPIRED) {
			remainingCount--;
		}
	}

	public void useHeld(LocalDate lessonDate) {
		if (lessonDate == null) {
			throw new CouponException(ExceptionCode.COUPON_INVALID_LESSON_DATE);
		}
		if (heldCount <= 0 || remainingCount <= 0) {
			throw new CouponException(ExceptionCode.COUPON_HOLD_STATE_CONFLICT);
		}
		if (firstUsedAt == null) {
			firstUsedAt = lessonDate.atStartOfDay();
			expiresAt = calculateExpiresAt(lessonDate);
		}
		heldCount--;
		remainingCount--;
		if (remainingCount == 0 && status == CouponStatus.ACTIVE) {
			status = CouponStatus.DEPLETED;
		}
	}

	public void deductHeld() {
		if (heldCount <= 0 || remainingCount <= 0) {
			throw new CouponException(ExceptionCode.COUPON_HOLD_STATE_CONFLICT);
		}
		heldCount--;
		remainingCount--;
		if (remainingCount == 0 && status == CouponStatus.ACTIVE) {
			status = CouponStatus.DEPLETED;
		}
	}

	public void useFreeChange() {
		if (freeChangeUsed) {
			throw new CouponException(ExceptionCode.COUPON_FREE_CHANGE_ALREADY_USED);
		}
		freeChangeUsed = true;
	}

	public int expire(LocalDate currentDate) {
		if (currentDate == null) {
			throw new CouponException(ExceptionCode.COUPON_INVALID_EXPIRY_DATE);
		}
		if (status != CouponStatus.ACTIVE
			|| expiresAt == null
			|| !isPastExpiryBoundary(currentDate)) {
			return 0;
		}
		final int expiredCount = remainingCount - heldCount;
		remainingCount = heldCount;
		status = CouponStatus.EXPIRED;
		return expiredCount;
	}

	private static Long requireMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new CouponException(ExceptionCode.COUPON_INVALID_MEMBER_ID);
		}
		return memberId;
	}

	private static CouponType requireType(CouponType type) {
		if (type == null) {
			throw new CouponException(ExceptionCode.COUPON_INVALID_TYPE);
		}
		return type;
	}

	private static int requireTotalCount(Integer totalCount) {
		if (totalCount == null || totalCount <= 0) {
			throw new CouponException(ExceptionCode.COUPON_INVALID_TOTAL_COUNT);
		}
		return totalCount;
	}

	private static int requireUsedCount(Integer usedCount, int totalCount) {
		if (usedCount == null || usedCount < 0 || usedCount > totalCount) {
			throw new CouponException(ExceptionCode.COUPON_INVALID_USED_COUNT);
		}
		return usedCount;
	}

	private void initializeUsageDates(int usedCount, LocalDate firstUsedDate) {
		if ((usedCount == 0) != (firstUsedDate == null)) {
			throw new CouponException(ExceptionCode.COUPON_INVALID_FIRST_USED_DATE);
		}
		if (firstUsedDate != null) {
			firstUsedAt = firstUsedDate.atStartOfDay();
			expiresAt = calculateExpiresAt(firstUsedDate);
		}
	}

	private void ensureImportableOn(LocalDate registrationDate) {
		if (registrationDate == null) {
			throw new CouponException(ExceptionCode.COUPON_INVALID_EXPIRY_DATE);
		}
		if (firstUsedAt == null) {
			return;
		}
		if (firstUsedAt.toLocalDate().isAfter(registrationDate)) {
			throw new CouponException(ExceptionCode.COUPON_FIRST_USED_DATE_IN_FUTURE);
		}
		if (isPastExpiryBoundary(registrationDate)) {
			throw new CouponException(ExceptionCode.COUPON_EXPIRED_REGISTRATION);
		}
	}

	private boolean isPastExpiryBoundary(LocalDate date) {
		return expiresAt != null && expiresAt.toLocalDate().isBefore(date);
	}

	private static LocalDateTime calculateExpiresAt(LocalDate firstUsedDate) {
		return firstUsedDate.plusMonths(VALIDITY_MONTHS).atStartOfDay();
	}

	private static String requireCreatedBy(String createdBy) {
		if (createdBy == null || createdBy.isBlank()) {
			throw new CouponException(ExceptionCode.COUPON_INVALID_CREATED_BY);
		}
		return createdBy;
	}

	public Long getId() {
		return id;
	}

	public Long getMemberId() {
		return memberId;
	}

	public CouponType getType() {
		return type;
	}

	public int getTotalCount() {
		return totalCount;
	}

	public int getRemainingCount() {
		return remainingCount;
	}

	public int getHeldCount() {
		return heldCount;
	}

	public LocalDateTime getFirstUsedAt() {
		return firstUsedAt;
	}

	public LocalDateTime getExpiresAt() {
		return expiresAt;
	}

	public boolean isFreeChangeUsed() {
		return freeChangeUsed;
	}

	public CouponStatus getStatus() {
		return status;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
