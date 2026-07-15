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

	public static final int COUPON_TOTAL_COUNT = 10;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "coupon_type", nullable = false)
	private CouponType type;

	@Column(name = "total_count", nullable = false)
	private byte totalCount;

	@Column(name = "remaining_count", nullable = false)
	private byte remainingCount;

	@Column(name = "held_count", nullable = false)
	private byte heldCount;

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

	private Coupon(Long memberId, CouponType type, Integer totalCount, String createdBy) {
		this.memberId = requireMemberId(memberId);
		this.type = requireType(type);
		this.totalCount = requireTotalCount(totalCount);
		this.remainingCount = totalCount.byteValue();
		this.heldCount = 0;
		this.freeChangeUsed = false;
		this.status = CouponStatus.ACTIVE;
		this.createdBy = requireCreatedBy(createdBy);
	}

	public static Coupon create(Long memberId, CouponType type, Integer totalCount, String createdBy) {
		return new Coupon(memberId, type, totalCount, createdBy);
	}

	public void hold(LocalDate lessonDate) {
		if (lessonDate == null) {
			throw new CouponException(ExceptionCode.COUPON_INVALID_LESSON_DATE);
		}
		if (status != CouponStatus.ACTIVE || heldCount >= remainingCount) {
			throw new CouponException(ExceptionCode.COUPON_HOLD_NOT_AVAILABLE);
		}
		if (expiresAt != null && expiresAt.toLocalDate().isBefore(lessonDate)) {
			throw new CouponException(ExceptionCode.COUPON_EXPIRED_FOR_LESSON);
		}
		heldCount++;
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
			expiresAt = firstUsedAt.plusMonths(3);
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

	public int expire(LocalDate currentDate) {
		if (currentDate == null) {
			throw new CouponException(ExceptionCode.COUPON_INVALID_EXPIRY_DATE);
		}
		if (status != CouponStatus.ACTIVE
			|| expiresAt == null
			|| !expiresAt.toLocalDate().isBefore(currentDate)) {
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

	private static byte requireTotalCount(Integer totalCount) {
		if (totalCount == null || totalCount != COUPON_TOTAL_COUNT) {
			throw new CouponException(ExceptionCode.COUPON_INVALID_TOTAL_COUNT);
		}
		return totalCount.byteValue();
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
