package com.horse.schedules.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservation_member_day_guards")
public class ReservationMemberDayGuard {

	@EmbeddedId
	private ReservationMemberDayGuardId id;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	protected ReservationMemberDayGuard() {
	}

	public ReservationMemberDayGuardId getId() {
		return id;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
