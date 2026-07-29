package com.horse.global.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import com.horse.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ApiDateTimeSerializationTest {

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void RFC3339_Z와_양수_음수_오프셋을_같은_절대_시점으로_해석해_Z로_반환한다() throws Exception {
		final OffsetDateTime expected = OffsetDateTime.parse("2026-07-22T01:00:00Z");

		assertNormalized("2026-07-22T01:00:00Z", expected);
		assertNormalized("2026-07-22T10:00:00+09:00", expected);
		assertNormalized("2026-07-21T20:00:00-05:00", expected);
	}

	@Test
	void 오프셋이_없는_절대_시점은_역직렬화를_거부한다() {
		assertThatThrownBy(() -> objectMapper.readValue(
			"{\"timestamp\":\"2026-07-22T10:00:00\"}",
			TimestampPayload.class
		)).isInstanceOf(JacksonException.class);
	}

	@Test
	void 날짜와_시각_전용_값은_현지_벽시계_의미를_유지한다() throws Exception {
		final LocalTemporalPayload payload = new LocalTemporalPayload(
			LocalDate.of(2026, 7, 22),
			LocalTime.of(13, 30)
		);

		final String json = objectMapper.writeValueAsString(payload);

		assertThat(json).contains("\"date\":\"2026-07-22\"");
		assertThat(json).contains("\"time\":\"13:30:00\"");
		assertThat(objectMapper.readValue(json, LocalTemporalPayload.class)).isEqualTo(payload);
	}

	private void assertNormalized(String value, OffsetDateTime expected) throws Exception {
		final TimestampPayload payload = objectMapper.readValue(
			"{\"timestamp\":\"" + value + "\"}",
			TimestampPayload.class
		);

		assertThat(payload.timestamp().toInstant()).isEqualTo(expected.toInstant());
		assertThat(objectMapper.writeValueAsString(payload))
			.contains("\"timestamp\":\"2026-07-22T01:00:00Z\"");
	}

	private record TimestampPayload(OffsetDateTime timestamp) {
	}

	private record LocalTemporalPayload(LocalDate date, LocalTime time) {
	}
}
