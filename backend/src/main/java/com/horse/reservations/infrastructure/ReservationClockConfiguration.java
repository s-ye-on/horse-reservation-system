package com.horse.reservations.infrastructure;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ReservationClockConfiguration {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	@Bean
	Clock reservationClock() {
		return Clock.system(SEOUL_ZONE);
	}
}
