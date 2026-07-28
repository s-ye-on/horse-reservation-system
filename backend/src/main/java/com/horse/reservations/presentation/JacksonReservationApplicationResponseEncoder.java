package com.horse.reservations.presentation;

import org.springframework.stereotype.Component;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.application.ReservationApplicationResponseEncoder;
import com.horse.reservations.application.ReservationApplicationResult;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.presentation.dto.ReservationApplicationResponse;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class JacksonReservationApplicationResponseEncoder
	implements ReservationApplicationResponseEncoder {

	private final ObjectMapper objectMapper;

	public JacksonReservationApplicationResponseEncoder(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public String encode(ReservationApplicationResult result) {
		try {
			return objectMapper.writeValueAsString(ReservationApplicationResponse.from(result));
		}
		catch (JacksonException exception) {
			throw new ReservationException(
				ExceptionCode.RESERVATION_RESPONSE_SERIALIZATION_FAILED);
		}
	}
}
