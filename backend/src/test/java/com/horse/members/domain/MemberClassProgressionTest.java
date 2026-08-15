package com.horse.members.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.exception.MemberException;

class MemberClassProgressionTest {

	private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 15, 9, 0);

	@Test
	void 신규_회원은_실제_일반_기승만으로_progression을_계산한다() {
		final Member member = managedMember();

		increaseGeneralRides(member, 70);

		assertThat(member.getGeneralRideCount()).isEqualTo(70);
		assertThat(member.progressionValue()).isEqualTo(70);
		assertThat(member.progressionGeneralRidingGrade()).isEqualTo(GeneralRidingGrade.CANTER_BEGINNER);
		assertThat(member.effectiveGeneralRidingGrade()).isEqualTo(GeneralRidingGrade.CANTER_BEGINNER);
	}

	@Test
	void 기존_회원_baseline은_설정_시점_실제_횟수와_threshold의_차이만_인정한다() {
		final Member member = existingMember();

		member.changeProgressionBaseline(GeneralRidingGrade.LARGE_ARENA_TROT, STARTED_AT);
		increaseGeneralRides(member, 44);

		assertThat(member.getGeneralRideCount()).isEqualTo(44);
		assertThat(member.getProgressionBaselineThreshold()).isEqualTo(26);
		assertThat(member.getProgressionBaselineActualRideCount()).isZero();
		assertThat(member.progressionValue()).isEqualTo(70);
		assertThat(member.progressionGeneralRidingGrade()).isEqualTo(GeneralRidingGrade.CANTER_BEGINNER);
	}

	@Test
	void baseline_변경과_해제는_관리_시작_경계를_재기준화하지_않는다() {
		final Member member = existingMember();
		member.changeProgressionBaseline(GeneralRidingGrade.CANTER_BEGINNER, STARTED_AT);
		final LocalDateTime originalStartedAt = member.getProgressionManagementStartedAt();
		increaseGeneralRides(member, 5);

		member.changeProgressionBaseline(
			GeneralRidingGrade.LARGE_ARENA_TROT,
			STARTED_AT.plusDays(3));

		assertThat(member.getProgressionManagementStartedAt()).isEqualTo(originalStartedAt);
		assertThat(member.getProgressionBaselineActualRideCount()).isEqualTo(5);
		assertThat(member.progressionValue()).isEqualTo(26);

		member.removeProgressionBaseline();

		assertThat(member.getProgressionManagementStartedAt()).isEqualTo(originalStartedAt);
		assertThat(member.progressionValue()).isEqualTo(5);
	}

	@Test
	void 특수_승인은_승인_직전_progression의_대마장_속보_부족분만_한_번_인정한다() {
		final Member member = managedMember();
		increaseGeneralRides(member, 20);

		final int firstCredit = member.changeSpecialApprovals(true, false);
		final int secondCredit = member.changeSpecialApprovals(true, true);

		assertThat(firstCredit).isEqualTo(6);
		assertThat(secondCredit).isZero();
		assertThat(member.getGeneralRideCount()).isEqualTo(20);
		assertThat(member.getSpecialApprovalProgressionCredit()).isEqualTo(6);
		assertThat(member.progressionValue()).isEqualTo(26);

		member.increaseGeneralRideCount();

		assertThat(member.progressionValue()).isEqualTo(27);
	}

	@Test
	void 이미_구보_progression인_회원의_특수_승인은_일반_등급을_낮추거나_credit을_더하지_않는다() {
		final Member member = managedMember();
		increaseGeneralRides(member, 100);

		member.changeSpecialApprovals(true, false);

		assertThat(member.getSpecialApprovalProgressionCredit()).isZero();
		assertThat(member.effectiveGeneralRidingGrade()).isEqualTo(GeneralRidingGrade.CANTER);
	}

	@Test
	void 특수_승인_해제와_재승인은_기존_credit을_회수하거나_중복하지_않는다() {
		final Member member = managedMember();
		increaseGeneralRides(member, 20);
		member.changeSpecialApprovals(true, false);

		member.changeSpecialApprovals(false, false);
		final int recognizedAgain = member.changeSpecialApprovals(false, true);

		assertThat(recognizedAgain).isZero();
		assertThat(member.getSpecialApprovalProgressionCredit()).isEqualTo(6);
		assertThat(member.progressionValue()).isEqualTo(26);
	}

	@Test
	void 특수_승인_중에는_baseline_교정으로_인정_progression을_대마장_속보_아래로_낮출_수_없다() {
		final Member member = existingMember();
		member.changeProgressionBaseline(GeneralRidingGrade.LARGE_ARENA_TROT, STARTED_AT);
		member.changeSpecialApprovals(true, false);

		assertThatThrownBy(member::removeProgressionBaseline)
			.isInstanceOfSatisfying(MemberException.class,
				exception -> assertThat(exception.code())
					.isEqualTo(ExceptionCode.MEMBER_SPECIAL_APPROVAL_PROGRESSION_CONFLICT.code()));

		member.changeSpecialApprovals(false, false);
		member.removeProgressionBaseline();

		assertThat(member.progressionValue()).isZero();
	}

	@Test
	void promotion_hold는_effective_class만_제한하고_progression은_계속_누적한다() {
		final Member member = managedMember();
		increaseGeneralRides(member, 70);
		member.changePromotionHold(GeneralRidingGrade.LARGE_ARENA_TROT);

		increaseGeneralRides(member, 30);

		assertThat(member.progressionGeneralRidingGrade()).isEqualTo(GeneralRidingGrade.CANTER);
		assertThat(member.effectiveGeneralRidingGrade()).isEqualTo(GeneralRidingGrade.LARGE_ARENA_TROT);

		member.removePromotionHold();

		assertThat(member.effectiveGeneralRidingGrade()).isEqualTo(GeneralRidingGrade.CANTER);
	}

	@Test
	void baseline_교정으로_progression이_hold보다_낮아져도_hold를_보존하고_등급을_올리지_않는다() {
		final Member member = existingMember();
		member.changeProgressionBaseline(GeneralRidingGrade.CANTER_BEGINNER, STARTED_AT);
		member.changePromotionHold(GeneralRidingGrade.CANTER_BEGINNER);

		member.changeProgressionBaseline(
			GeneralRidingGrade.LARGE_ARENA_TROT,
			STARTED_AT.plusDays(1));

		assertThat(member.progressionGeneralRidingGrade()).isEqualTo(GeneralRidingGrade.LARGE_ARENA_TROT);
		assertThat(member.getPromotionHoldClass()).isEqualTo(GeneralRidingGrade.CANTER_BEGINNER);
		assertThat(member.effectiveGeneralRidingGrade()).isEqualTo(GeneralRidingGrade.LARGE_ARENA_TROT);
	}

	@Test
	void promotion_hold와_특수_승인은_어느_방향에서도_동시에_활성화할_수_없다() {
		final Member holdMember = managedMember();
		increaseGeneralRides(holdMember, 26);
		holdMember.changePromotionHold(GeneralRidingGrade.ROUND_TROT);

		assertThatThrownBy(() -> holdMember.changeSpecialApprovals(true, false))
			.isInstanceOfSatisfying(MemberException.class,
				exception -> assertThat(exception.code()).isEqualTo("MEMBER_CLASS_POLICY_CONFLICT"));

		final Member approvedMember = managedMember();
		approvedMember.changeSpecialApprovals(true, false);

		assertThatThrownBy(() -> approvedMember.changePromotionHold(GeneralRidingGrade.FIRST_RIDE))
			.isInstanceOfSatisfying(MemberException.class,
				exception -> assertThat(exception.code()).isEqualTo("MEMBER_CLASS_POLICY_CONFLICT"));
	}

	@Test
	void 특수_승인_credit_하향_교정은_모든_특수_승인_해제_후에만_허용한다() {
		final Member member = managedMember();
		increaseGeneralRides(member, 20);
		member.changeSpecialApprovals(true, false);

		assertThatThrownBy(() -> member.correctSpecialApprovalProgressionCredit(0))
			.isInstanceOf(MemberException.class);

		member.changeSpecialApprovals(false, false);
		member.correctSpecialApprovalProgressionCredit(0);

		assertThat(member.progressionValue()).isEqualTo(20);
	}

	@Test
	void 관리가_시작되지_않은_기존_회원은_특수_승인과_hold를_변경할_수_없다() {
		final Member member = existingMember();

		assertThatThrownBy(() -> member.changeSpecialApprovals(true, false))
			.isInstanceOfSatisfying(MemberException.class,
				exception -> assertThat(exception.code())
					.isEqualTo(ExceptionCode.MEMBER_PROGRESSION_NOT_INITIALIZED.code()));
		assertThatThrownBy(() -> member.changePromotionHold(GeneralRidingGrade.FIRST_RIDE))
			.isInstanceOf(MemberException.class);
	}

	private Member managedMember() {
		return Member.createManaged("managed-member", "관리 회원", "010-0000-0000", STARTED_AT);
	}

	private Member existingMember() {
		return Member.create("existing-member", "기존 회원", "010-0000-0000");
	}

	private void increaseGeneralRides(Member member, int count) {
		for (int index = 0; index < count; index++) {
			member.increaseGeneralRideCount();
		}
	}
}
