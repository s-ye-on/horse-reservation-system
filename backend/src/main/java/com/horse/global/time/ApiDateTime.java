package com.horse.global.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class ApiDateTime {

	public static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private ApiDateTime() {
	}

	public static OffsetDateTime toSeoulOffset(LocalDateTime value) {
		return value == null ? null : value.atZone(SEOUL_ZONE).toOffsetDateTime();
	}

	public static OffsetDateTime nowInSeoul() {
		return OffsetDateTime.now(SEOUL_ZONE);
	}
}
