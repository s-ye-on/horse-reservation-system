package com.horse.reservations.domain;

import java.time.LocalDateTime;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservation_application_idempotencies")
public class ReservationApplicationIdempotency {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "auth_subject", nullable = false, updatable = false)
	private String authSubject;

	@Column(name = "operation", nullable = false, updatable = false)
	private ReservationApplicationOperation operation;

	@Column(name = "idempotency_key", nullable = false, updatable = false)
	private String idempotencyKey;

	@Column(name = "request_fingerprint", nullable = false, updatable = false)
	private String requestFingerprint;

	@Column(name = "status", nullable = false)
	private ReservationApplicationIdempotencyStatus status;

	@Column(name = "http_status")
	private Integer httpStatus;

	@Column(name = "response_body", columnDefinition = "LONGTEXT")
	private String responseBody;

	@Column(name = "reservation_id")
	private Long reservationId;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	protected ReservationApplicationIdempotency() {
	}

	public void ensureSameFingerprint(String fingerprint) {
		if (!requestFingerprint.equals(fingerprint)) {
			throw new ReservationException(ExceptionCode.RESERVATION_IDEMPOTENCY_KEY_CONFLICT);
		}
	}

	public void complete(
		Long completedReservationId,
		int completedHttpStatus,
		String completedResponseBody,
		LocalDateTime completionTime
	) {
		if (status != ReservationApplicationIdempotencyStatus.PROCESSING
			|| completedReservationId == null
			|| completedReservationId <= 0
			|| completedHttpStatus < 100
			|| completedHttpStatus > 599
			|| completedResponseBody == null
			|| completedResponseBody.isBlank()
			|| completionTime == null) {
			throw new ReservationException(
				ExceptionCode.RESERVATION_IDEMPOTENCY_STATE_CONFLICT);
		}
		status = ReservationApplicationIdempotencyStatus.COMPLETED;
		reservationId = completedReservationId;
		httpStatus = completedHttpStatus;
		responseBody = completedResponseBody;
		completedAt = completionTime;
	}

	public boolean isCompleted() {
		return status == ReservationApplicationIdempotencyStatus.COMPLETED;
	}

	public Integer getHttpStatus() {
		return httpStatus;
	}

	public String getResponseBody() {
		return responseBody;
	}

	public Long getReservationId() {
		return reservationId;
	}
}
