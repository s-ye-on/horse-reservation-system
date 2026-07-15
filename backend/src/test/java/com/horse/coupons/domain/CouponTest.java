package com.horse.coupons.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

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

	@Test
	void 쿠폰은_사용_가능_횟수_안에서_점유하고_해제한다() {
		final Coupon coupon = Coupon.create(1L, CouponType.GENERAL, 10, "admin-subject");

		coupon.hold(LocalDate.of(2026, 8, 10));
		coupon.releaseHold();

		assertThat(coupon.getHeldCount()).isZero();
		assertThat(coupon.getRemainingCount()).isEqualTo(10);
	}

	@Test
	void 점유된_쿠폰을_처음_사용하면_수업일로부터_삼개월의_유효기간을_설정하고_차감한다() {
		final Coupon coupon = Coupon.create(1L, CouponType.GENERAL, 10, "admin-subject");
		final LocalDate lessonDate = LocalDate.of(2026, 8, 10);
		coupon.hold(lessonDate);

		coupon.useHeld(lessonDate);

		assertThat(coupon.getRemainingCount()).isEqualTo(9);
		assertThat(coupon.getHeldCount()).isZero();
		assertThat(coupon.getFirstUsedAt()).isEqualTo(lessonDate.atStartOfDay());
		assertThat(coupon.getExpiresAt()).isEqualTo(lessonDate.plusMonths(3).atStartOfDay());
	}

	@Test
	void 만료일_다음_날에는_점유되지_않은_잔여_횟수만_소멸한다() {
		final Coupon coupon = usedCoupon();
		coupon.hold(LocalDate.of(2026, 10, 31));
		coupon.hold(LocalDate.of(2026, 10, 31));

		final int boundaryExpiredCount = coupon.expire(LocalDate.of(2026, 11, 1));
		final int expiredCount = coupon.expire(LocalDate.of(2026, 11, 2));

		assertThat(boundaryExpiredCount).isZero();
		assertThat(expiredCount).isEqualTo(7);
		assertThat(coupon.getStatus()).isEqualTo(CouponStatus.EXPIRED);
		assertThat(coupon.getRemainingCount()).isEqualTo(2);
		assertThat(coupon.getHeldCount()).isEqualTo(2);
	}

	@Test
	void 만료_전에_점유한_횟수는_반환하거나_사용해도_잔여분으로_복구되지_않는다() {
		final Coupon coupon = usedCoupon();
		coupon.hold(LocalDate.of(2026, 10, 31));
		coupon.hold(LocalDate.of(2026, 10, 31));
		coupon.expire(LocalDate.of(2026, 11, 2));

		coupon.releaseHold();
		coupon.useHeld(LocalDate.of(2026, 11, 3));

		assertThat(coupon.getStatus()).isEqualTo(CouponStatus.EXPIRED);
		assertThat(coupon.getRemainingCount()).isZero();
		assertThat(coupon.getHeldCount()).isZero();
	}

	@Test
	void 쿠폰_만료_기준일이_없으면_만료할_수_없다() {
		final Coupon coupon = usedCoupon();

		assertThatThrownBy(() -> coupon.expire(null))
			.isInstanceOf(CouponException.class)
			.hasMessage(ExceptionCode.COUPON_INVALID_EXPIRY_DATE.message());
	}

	private Coupon usedCoupon() {
		final Coupon coupon = Coupon.create(1L, CouponType.GENERAL, 10, "admin-subject");
		final LocalDate firstLessonDate = LocalDate.of(2026, 8, 1);
		coupon.hold(firstLessonDate);
		coupon.useHeld(firstLessonDate);
		return coupon;
	}
}
