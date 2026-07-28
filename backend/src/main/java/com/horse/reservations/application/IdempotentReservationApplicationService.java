package com.horse.reservations.application;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.exception.MemberException;
import com.horse.reservations.domain.ReservationApplicationIdempotency;
import com.horse.reservations.domain.ReservationApplicationOperation;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.infrastructure.ReservationApplicationIdempotencyClaimRepository;
import com.horse.reservations.infrastructure.ReservationApplicationIdempotencyRepository;

@Service
public class IdempotentReservationApplicationService {

	private static final int CREATED_HTTP_STATUS = 201;
	private static final int MAX_AUTH_SUBJECT_LENGTH = 191;
	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;
	private static final ReservationApplicationOperation OPERATION =
		ReservationApplicationOperation.MEMBER_RESERVATION_CREATE;

	private final Clock clock;
	private final ReservationApplicationService reservationApplicationService;
	private final ReservationApplicationResponseEncoder responseEncoder;
	private final ReservationApplicationIdempotencyClaimRepository claimRepository;
	private final ReservationApplicationIdempotencyRepository idempotencyRepository;

	public IdempotentReservationApplicationService(
		Clock clock,
		ReservationApplicationService reservationApplicationService,
		ReservationApplicationResponseEncoder responseEncoder,
		ReservationApplicationIdempotencyClaimRepository claimRepository,
		ReservationApplicationIdempotencyRepository idempotencyRepository
	) {
		this.clock = clock;
		this.reservationApplicationService = reservationApplicationService;
		this.responseEncoder = responseEncoder;
		this.claimRepository = claimRepository;
		this.idempotencyRepository = idempotencyRepository;
	}

	@Transactional
	public IdempotentReservationApplicationResult apply(
		String authSubject,
		String idempotencyKey,
		Long timeSlotId,
		String classType
	) {
		final String validatedSubject = validateAuthSubject(authSubject);
		final String normalizedKey = normalizeKey(idempotencyKey);
		final String fingerprint = ReservationApplicationFingerprint
			.create(timeSlotId, classType)
			.value();
		final boolean claimed = claimRepository.claim(
			validatedSubject,
			OPERATION,
			normalizedKey,
			fingerprint);
		final ReservationApplicationIdempotency idempotency = idempotencyRepository.findForUpdate(
				validatedSubject,
				OPERATION,
				normalizedKey)
			.orElseThrow(() -> new ReservationException(
				ExceptionCode.RESERVATION_IDEMPOTENCY_STATE_CONFLICT));
		idempotency.ensureSameFingerprint(fingerprint);
		if (!claimed) {
			return replay(idempotency);
		}

		final ReservationApplicationResult applicationResult = reservationApplicationService.apply(
			validatedSubject,
			timeSlotId,
			classType);
		final String responseBody = responseEncoder.encode(applicationResult);
		idempotency.complete(
			applicationResult.reservationId(),
			CREATED_HTTP_STATUS,
			responseBody,
			LocalDateTime.now(clock));
		idempotencyRepository.saveAndFlush(idempotency);
		return new IdempotentReservationApplicationResult(CREATED_HTTP_STATUS, responseBody);
	}

	private IdempotentReservationApplicationResult replay(
		ReservationApplicationIdempotency idempotency
	) {
		if (!idempotency.isCompleted()
			|| idempotency.getHttpStatus() == null
			|| idempotency.getResponseBody() == null
			|| idempotency.getReservationId() == null) {
			throw new ReservationException(
				ExceptionCode.RESERVATION_IDEMPOTENCY_STATE_CONFLICT);
		}
		return new IdempotentReservationApplicationResult(
			idempotency.getHttpStatus(),
			idempotency.getResponseBody());
	}

	private String validateAuthSubject(String authSubject) {
		if (authSubject == null
			|| authSubject.isBlank()
			|| authSubject.length() > MAX_AUTH_SUBJECT_LENGTH) {
			throw new MemberException(ExceptionCode.MEMBER_INVALID_AUTH_SUBJECT);
		}
		return authSubject;
	}

	private String normalizeKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new ReservationException(
				ExceptionCode.RESERVATION_IDEMPOTENCY_KEY_REQUIRED);
		}
		final String normalizedKey = idempotencyKey.strip();
		if (normalizedKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw new ReservationException(
				ExceptionCode.RESERVATION_INVALID_IDEMPOTENCY_KEY);
		}
		return normalizedKey;
	}
}
