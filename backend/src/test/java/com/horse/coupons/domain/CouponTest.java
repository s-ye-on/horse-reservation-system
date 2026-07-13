package com.horse.coupons.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.horse.coupons.domain.exception.CouponException;
import com.horse.global.exception.ExceptionCode;

class CouponTest {

	@Test
	void 신규_쿠폰은_10회권의_미사용_상태로_생성된다() {
		final Coupon coupon = Coupon.create(1L, CouponType.GENERAL, 10, "admin-subject");

		assertThat(coupon.getMemberId()).isEqualTo(1L);
		assertThat(coupon.getType()).isEqualTo(CouponType.GENERAL);
		assertThat(coupon.getTotalCount()).isEqualTo(10);
		assertThat(coupon.getRemainingCount()).isEqualTo(10);
		assertThat(coupon.getHeldCount()).isZero();
		assertThat(coupon.getFirstUsedAt()).isNull();
		assertThat(coupon.getExpiresAt()).isNull();
		assertThat(coupon.isFreeChangeUsed()).isFalse();
		assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
		assertThat(coupon.getCreatedBy()).isEqualTo("admin-subject");
	}

	@Test
	void 열_회가_아닌_쿠폰은_생성할_수_없다() {
		assertThatThrownBy(() -> Coupon.create(1L, CouponType.GENERAL, 9, "admin-subject"))
			.isInstanceOf(CouponException.class)
			.hasMessage(ExceptionCode.COUPON_INVALID_TOTAL_COUNT.message());
	}

	@Test
	void 등록_관리자_식별자가_없으면_쿠폰을_생성할_수_없다() {
		assertThatThrownBy(() -> Coupon.create(1L, CouponType.GENERAL, 10, " "))
			.isInstanceOf(CouponException.class)
			.hasMessage(ExceptionCode.COUPON_INVALID_CREATED_BY.message());
	}
}
