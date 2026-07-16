package com.horse.global.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.horse.coupons.application.CouponExpiryResult;
import com.horse.coupons.application.CouponExpiryService;
import com.horse.coupons.presentation.CouponExpiryScheduler;
import com.horse.reservations.application.PendingPaymentExpiryResult;
import com.horse.reservations.application.PendingPaymentExpiryService;
import com.horse.reservations.presentation.AdminPendingPaymentExpiryJobController;
import com.horse.reservations.presentation.PendingPaymentExpiryScheduler;
import com.horse.reservations.presentation.dto.PendingPaymentExpiryResponse;

@ExtendWith(OutputCaptureExtension.class)
class OperationalJobBoundaryTest {

	private static final LocalDateTime EXECUTED_AT = LocalDateTime.of(2026, 7, 17, 10, 0);

	private final OperationalJobLogger jobLogger = new OperationalJobLogger();

	@Test
	void 쿠폰_만료_스케줄러는_시스템_실행_결과를_기록한다(CapturedOutput output) {
		final CouponExpiryService service = mock(CouponExpiryService.class);
		when(service.expireDueCoupons()).thenReturn(new CouponExpiryResult(2, 11));
		final CouponExpiryScheduler scheduler = new CouponExpiryScheduler(service, jobLogger);

		scheduler.expireCoupons();

		assertThat(output)
			.contains("jobName=COUPON_EXPIRY")
			.contains("trigger=SCHEDULED")
			.contains("actorType=SYSTEM")
			.contains("result=expiredCouponCount=2,expiredAvailableCount=11");
	}

	@Test
	void 입금대기_만료_스케줄러는_실패를_기록하고_다시_전파한다(CapturedOutput output) {
		final PendingPaymentExpiryService service = mock(PendingPaymentExpiryService.class);
		final IllegalStateException failure = new IllegalStateException("lock timeout");
		when(service.expireDuePayments()).thenThrow(failure);
		final PendingPaymentExpiryScheduler scheduler = new PendingPaymentExpiryScheduler(service, jobLogger);

		assertThatThrownBy(scheduler::expirePendingPayments).isSameAs(failure);
		assertThat(output)
			.contains("jobName=PENDING_PAYMENT_EXPIRY")
			.contains("trigger=SCHEDULED")
			.contains("actorType=SYSTEM")
			.contains("exceptionType=java.lang.IllegalStateException");
	}

	@Test
	void 관리자_수동_만료는_JWT_sub와_처리_결과를_기록한다(CapturedOutput output) {
		final PendingPaymentExpiryService service = mock(PendingPaymentExpiryService.class);
		when(service.expireDuePayments()).thenReturn(new PendingPaymentExpiryResult(3, EXECUTED_AT));
		final AdminPendingPaymentExpiryJobController controller =
			new AdminPendingPaymentExpiryJobController(service, jobLogger);

		final PendingPaymentExpiryResponse response = controller.expirePendingPayments("admin-subject");

		assertThat(response.expiredCount()).isEqualTo(3);
		assertThat(output)
			.contains("jobName=PENDING_PAYMENT_EXPIRY")
			.contains("trigger=MANUAL")
			.contains("actorType=ADMIN")
			.contains("actorSubject=admin-subject")
			.contains("result=expiredCount=3,executedAt=2026-07-17T10:00");
	}
}
