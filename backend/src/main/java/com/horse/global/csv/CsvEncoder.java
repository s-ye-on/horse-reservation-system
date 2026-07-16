package com.horse.global.csv;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CsvEncoder {

	private static final String UTF_8_BOM = "\uFEFF";
	private static final String LINE_SEPARATOR = "\r\n";
	private static final char QUOTE = '"';

	private CsvEncoder() {
	}

	public static byte[] encode(List<String> headers, List<? extends List<String>> rows) {
		final StringBuilder csv = new StringBuilder(UTF_8_BOM);
		appendRow(csv, headers);
		for (List<String> row : rows) {
			appendRow(csv, row);
		}
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static void appendRow(StringBuilder csv, List<String> values) {
		for (int index = 0; index < values.size(); index++) {
			if (index > 0) {
				csv.append(',');
			}
			csv.append(quote(values.get(index)));
		}
		csv.append(LINE_SEPARATOR);
	}

	private static String quote(String value) {
		final String normalized = value == null ? "" : value;
		final String protectedValue = startsWithFormulaOperator(normalized) ? "'" + normalized : normalized;
		return QUOTE + protectedValue.replace("\"", "\"\"") + QUOTE;
	}

	private static boolean startsWithFormulaOperator(String value) {
		final String stripped = value.stripLeading();
		if (stripped.isEmpty()) {
			return false;
		}
		return switch (stripped.charAt(0)) {
			case '=', '+', '-', '@', '\t', '\r' -> true;
			default -> false;
		};
	}
}
