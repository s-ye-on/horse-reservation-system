package com.horse.global.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class OperationalJobLoggerTest {

	private final OperationalJobLogger jobLogger = new OperationalJobLogger();

	@Test
	void 자동_작업의_성공_로그에_작업명과_시스템_행위자를_기록한다(CapturedOutput output) {
		final String result = jobLogger.execute(
			OperationalJobContext.scheduled(OperationalJobName.COUPON_EXPIRY),
			() -> "done",
			value -> "status=" + value);

		assertThat(result).isEqualTo("done");
		assertThat(output)
			.contains("Operational job completed.")
			.contains("jobName=COUPON_EXPIRY")
			.contains("trigger=SCHEDULED")
			.contains("actorType=SYSTEM")
			.contains("actorSubject=system")
			.contains("result=status=done");
	}

	@Test
	void 수동_작업의_실패_로그에_관리자와_예외를_기록하고_다시_전파한다(CapturedOutput output) {
		final IllegalStateException failure = new IllegalStateException("database unavailable");
		final OperationalJobContext context = OperationalJobContext.manual(
			OperationalJobName.PENDING_PAYMENT_EXPIRY, "admin-subject");

		assertThatThrownBy(() -> jobLogger.execute(
			context,
			() -> {
				throw failure;
			},
			result -> "unused"))
			.isSameAs(failure);
		assertThat(output)
			.contains("Operational job failed.")
			.contains("jobName=PENDING_PAYMENT_EXPIRY")
			.contains("trigger=MANUAL")
			.contains("actorType=ADMIN")
			.contains("actorSubject=admin-subject")
			.contains("exceptionType=java.lang.IllegalStateException")
			.contains("database unavailable");
	}
}
