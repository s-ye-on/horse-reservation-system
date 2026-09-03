package com.horse.members.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.exception.MemberException;

class MemberRideCountAdjustmentTest {

	private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 16, 9, 0);

	@Test
	void actual_count만_delta로_보정하고_baseline과_관리_시작_경계를_유지한다() {
		final Member member = Member.createManaged(
			"m32-07-baseline-member",
			"보정 회원",
			"010-0000-0001",
			STARTED_AT);
		member.changeProgressionBaseline(GeneralRidingGrade.LARGE_ARENA_TROT, STARTED_AT);

		member.adjustGeneralRideCount(2);

		assertThat(member.getGeneralRideCount()).isEqualTo(2);
		assertThat(member.progressionValue()).isEqualTo(28);
		assertThat(member.getProgressionManagementStartedAt()).isEqualTo(STARTED_AT);
		assertThat(member.getProgressionBaselineClass()).isEqualTo(GeneralRidingGrade.LARGE_ARENA_TROT);
		assertThat(member.getProgressionBaselineThreshold()).isEqualTo(26);
		assertThat(member.getProgressionBaselineActualRideCount()).isZero();
	}

	@Test
	void actual_count_보정은_특수_승인_credit을_변경하지_않는다() {
		final Member member = Member.createManaged(
			"m32-07-special-member",
			"승인 회원",
			"010-0000-0002",
			STARTED_AT);
		member.changeSpecialApprovals(true, false);

		member.adjustGeneralRideCount(1);

		assertThat(member.getGeneralRideCount()).isOne();
		assertThat(member.getSpecialApprovalProgressionCredit()).isEqualTo(26);
		assertThat(member.progressionValue()).isEqualTo(27);
	}

	@Test
	void 초기화되지_않았거나_0_delta이거나_음수가_되는_보정은_거부한다() {
		final Member uninitialized = Member.create(
			"m32-07-uninitialized",
			"미초기화 회원",
			"010-0000-0003");
		final Member managed = Member.createManaged(
			"m32-07-invalid",
			"보정 회원",
			"010-0000-0004",
			STARTED_AT);

		assertMemberException(
			() -> uninitialized.adjustGeneralRideCount(1),
			ExceptionCode.MEMBER_PROGRESSION_NOT_INITIALIZED);
		assertMemberException(
			() -> managed.adjustGeneralRideCount(0),
			ExceptionCode.MEMBER_INVALID_RIDE_COUNT_ADJUSTMENT);
		assertMemberException(
			() -> managed.adjustGeneralRideCount(-1),
			ExceptionCode.MEMBER_INVALID_RIDE_COUNT_ADJUSTMENT);
		assertThat(managed.getGeneralRideCount()).isZero();
	}

	private void assertMemberException(Runnable action, ExceptionCode expectedCode) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(MemberException.class,
				exception -> assertThat(exception.code()).isEqualTo(expectedCode.code()));
	}
}
