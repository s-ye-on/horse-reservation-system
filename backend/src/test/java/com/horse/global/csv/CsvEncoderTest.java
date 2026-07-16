package com.horse.global.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class CsvEncoderTest {

	@Test
	void UTF8_BOM과_CRLF와_필드_인용을_적용한다() {
		final byte[] encoded = CsvEncoder.encode(
			List.of("name", "memo"),
			List.of(List.of("김,하늘", "따옴표 \"확인\"")));

		final String csv = new String(encoded, StandardCharsets.UTF_8);

		assertThat(encoded).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
		assertThat(csv).isEqualTo("\uFEFF\"name\",\"memo\"\r\n"
			+ "\"김,하늘\",\"따옴표 \"\"확인\"\"\"\r\n");
	}

	@Test
	void 스프레드시트_수식으로_해석될_값을_문자열로_보호한다() {
		final byte[] encoded = CsvEncoder.encode(
			List.of("equals", "plus", "minus", "at", "spaced"),
			List.of(List.of("=1+1", "+cmd", "-2+3", "@sum", "  =hidden")));

		final String csv = new String(encoded, StandardCharsets.UTF_8);

		assertThat(csv).contains("\"'=1+1\"");
		assertThat(csv).contains("\"'+cmd\"");
		assertThat(csv).contains("\"'-2+3\"");
		assertThat(csv).contains("\"'@sum\"");
		assertThat(csv).contains("\"'  =hidden\"");
	}
}
