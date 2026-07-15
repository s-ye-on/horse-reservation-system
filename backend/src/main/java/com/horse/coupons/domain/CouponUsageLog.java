package com.horse.coupons.domain;

import java.time.LocalDateTime;

import com.horse.coupons.domain.exception.CouponException;
import com.horse.global.exception.ExceptionCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "coupon_usage_logs")
public class CouponUsageLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "coupon_id", nullable = false)
	private Long couponId;

	@Column(name = "reservation_id")
	private Long reservationId;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "action", nullable = false)
	private CouponUsageAction action;

	@Column(name = "count_delta", nullable = false)
	private short countDelta;

	@Column(name = "occurred_at", nullable = false)
	private LocalDateTime occurredAt;

	@Column(name = "actor_type", nullable = false)
	private CouponActorType actorType;

	@Column(name = "memo")
	private String memo;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	protected CouponUsageLog() {
	}

	private CouponUsageLog(
		Long couponId,
		Long reservationId,
		Long memberId,
		CouponUsageAction action,
		short countDelta,
		LocalDateTime occurredAt,
		CouponActorType actorType
	) {
		this.couponId = requireId(couponId);
		this.reservationId = requireId(reservationId);
		this.memberId = requireId(memberId);
		this.action = action;
		this.countDelta = countDelta;
		this.occurredAt = requireOccurredAt(occurredAt);
		this.actorType = requireActorType(actorType);
	}

	public static CouponUsageLog held(
		Long couponId,
		Long reservationId,
		Long memberId,
		LocalDateTime occurredAt,
		CouponActorType actorType
	) {
		return new CouponUsageLog(
			couponId,
			reservationId,
			memberId,
			CouponUsageAction.HELD,
			(short) 1,
			occurredAt,
			actorType);
	}

	public static CouponUsageLog released(
		Long couponId,
		Long reservationId,
		Long memberId,
		LocalDateTime occurredAt,
		CouponActorType actorType
	) {
		return new CouponUsageLog(
			couponId,
			reservationId,
			memberId,
			CouponUsageAction.RELEASED,
			(short) -1,
			occurredAt,
			actorType);
	}

	public static CouponUsageLog confirmed(
		Long couponId,
		Long reservationId,
		Long memberId,
		LocalDateTime occurredAt,
		CouponActorType actorType
	) {
		return new CouponUsageLog(
			couponId,
			reservationId,
			memberId,
			CouponUsageAction.CONFIRMED,
			(short) 0,
			occurredAt,
			actorType);
	}

	public static CouponUsageLog used(
		Long couponId,
		Long reservationId,
		Long memberId,
		LocalDateTime occurredAt,
		CouponActorType actorType
	) {
		return new CouponUsageLog(
			couponId,
			reservationId,
			memberId,
			CouponUsageAction.USED,
			(short) -1,
			occurredAt,
			actorType);
	}

	public static CouponUsageLog deducted(
		Long couponId,
		Long reservationId,
		Long memberId,
		LocalDateTime occurredAt,
		CouponActorType actorType
	) {
		return new CouponUsageLog(
			couponId,
			reservationId,
			memberId,
			CouponUsageAction.DEDUCTED,
			(short) -1,
			occurredAt,
			actorType);
	}

	private static Long requireId(Long id) {
		if (id == null || id <= 0) {
			throw new CouponException(ExceptionCode.COUPON_INVALID_USAGE_REFERENCE);
		}
		return id;
	}

	private static LocalDateTime requireOccurredAt(LocalDateTime occurredAt) {
		if (occurredAt == null) {
			throw new CouponException(ExceptionCode.COUPON_INVALID_USAGE_OCCURRED_AT);
		}
		return occurredAt;
	}

	private static CouponActorType requireActorType(CouponActorType actorType) {
		if (actorType == null) {
			throw new CouponException(ExceptionCode.COUPON_INVALID_ACTOR_TYPE);
		}
		return actorType;
	}

	public Long getId() {
		return id;
	}

	public Long getCouponId() {
		return couponId;
	}

	public Long getReservationId() {
		return reservationId;
	}

	public Long getMemberId() {
		return memberId;
	}

	public CouponUsageAction getAction() {
		return action;
	}

	public int getCountDelta() {
		return countDelta;
	}

	public LocalDateTime getOccurredAt() {
		return occurredAt;
	}

	public CouponActorType getActorType() {
		return actorType;
	}

	public String getMemo() {
		return memo;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
