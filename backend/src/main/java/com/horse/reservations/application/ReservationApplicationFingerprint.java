package com.horse.reservations.application;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public record ReservationApplicationFingerprint(String value) {

	private static final int FINGERPRINT_FORMAT_VERSION = 1;
	private static final String HASH_ALGORITHM = "SHA-256";

	public static ReservationApplicationFingerprint create(Long timeSlotId, String classType) {
		try {
			final byte[] canonicalRequest = canonicalRequest(timeSlotId, classType);
			final byte[] digest = MessageDigest.getInstance(HASH_ALGORITHM).digest(canonicalRequest);
			return new ReservationApplicationFingerprint(HexFormat.of().formatHex(digest));
		}
		catch (IOException | NoSuchAlgorithmException exception) {
			throw new ReservationException(
				ExceptionCode.RESERVATION_IDEMPOTENCY_FINGERPRINT_FAILED);
		}
	}

	private static byte[] canonicalRequest(Long timeSlotId, String classType) throws IOException {
		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream output = new DataOutputStream(bytes)) {
			output.writeInt(FINGERPRINT_FORMAT_VERSION);
			writeLong(output, timeSlotId);
			writeString(output, classType);
			output.flush();
			return bytes.toByteArray();
		}
	}

	private static void writeLong(DataOutputStream output, Long value) throws IOException {
		output.writeBoolean(value != null);
		if (value != null) {
			output.writeLong(value);
		}
	}

	private static void writeString(DataOutputStream output, String value) throws IOException {
		output.writeBoolean(value != null);
		if (value != null) {
			final byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
			output.writeInt(encoded.length);
			output.write(encoded);
		}
	}
}
