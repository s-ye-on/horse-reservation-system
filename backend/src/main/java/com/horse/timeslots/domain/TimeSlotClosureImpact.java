package com.horse.timeslots.domain;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.timeslots.domain.exception.TimeSlotException;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "time_slot_closure_impacts")
public class TimeSlotClosureImpact {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "closure_id", nullable = false, updatable = false)
	private Long closureId;

	@Column(name = "reservation_id", nullable = false, updatable = false)
	private Long reservationId;

	@Column(name = "reservation_status_at_start", nullable = false, updatable = false)
	private ReservationStatus reservationStatusAtStart;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	protected TimeSlotClosureImpact() {
	}

	private TimeSlotClosureImpact(
		Long closureId,
		Long reservationId,
		ReservationStatus reservationStatusAtStart
	) {
		if (closureId == null || reservationId == null || reservationStatusAtStart == null) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_INVALID_CLOSURE_REFERENCE);
		}
		this.closureId = closureId;
		this.reservationId = reservationId;
		this.reservationStatusAtStart = reservationStatusAtStart;
	}

	public static TimeSlotClosureImpact create(
		Long closureId,
		Long reservationId,
		ReservationStatus reservationStatusAtStart
	) {
		return new TimeSlotClosureImpact(closureId, reservationId, reservationStatusAtStart);
	}

	public Long getId() {
		return id;
	}

	public Long getClosureId() {
		return closureId;
	}

	public Long getReservationId() {
		return reservationId;
	}

	public ReservationStatus getReservationStatusAtStart() {
		return reservationStatusAtStart;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
