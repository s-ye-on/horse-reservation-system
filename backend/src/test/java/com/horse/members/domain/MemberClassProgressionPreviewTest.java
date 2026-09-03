package com.horse.members.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class MemberClassProgressionPreviewTest {

	private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 21, 9, 0);

	@Test
	void preview는_상태를_바꾸지_않고_baseline_Command와_동일한_결과를_계산한다() {
		final Member member = managedMember("preview-baseline", 10);
		final MemberClassProgressionProjection before = member.currentClassProgressionProjection();

		final MemberClassProgressionProjection preview = member.previewProgressionBaseline(
			GeneralRidingGrade.CANTER_BEGINNER);

		assertThat(member.currentClassProgressionProjection()).isEqualTo(before);
		assertThat(preview.progressionValue()).isEqualTo(70);
		assertThat(preview.progressionClass()).isEqualTo(GeneralRidingGrade.CANTER_BEGINNER);
		assertThat(preview.baselineActualRideCount()).isEqualTo(10);

		member.changeProgressionBaseline(GeneralRidingGrade.CANTER_BEGINNER, STARTED_AT.plusDays(1));
		assertThat(member.currentClassProgressionProjection()).isEqualTo(preview);
		assertThat(member.getProgressionManagementStartedAt()).isEqualTo(STARTED_AT);
	}

	@Test
	void hold와_횟수_보정_preview는_각_Command와_동일하고_다른_progression_입력을_보존한다() {
		final Member member = managedMember("preview-hold", 100);
		member.changeProgressionBaseline(GeneralRidingGrade.LARGE_ARENA_TROT, STARTED_AT);

		final MemberClassProgressionProjection holdPreview = member.previewPromotionHold(
			GeneralRidingGrade.LARGE_ARENA_TROT);
		member.changePromotionHold(GeneralRidingGrade.LARGE_ARENA_TROT);
		assertThat(member.currentClassProgressionProjection()).isEqualTo(holdPreview);

		final MemberClassProgressionProjection adjustmentPreview = member.previewGeneralRideCountAdjustment(2);
		member.adjustGeneralRideCount(2);
		assertThat(member.currentClassProgressionProjection()).isEqualTo(adjustmentPreview);
		assertThat(adjustmentPreview.baselineClass()).isEqualTo(GeneralRidingGrade.LARGE_ARENA_TROT);
		assertThat(adjustmentPreview.effectiveClass()).isEqualTo(GeneralRidingGrade.LARGE_ARENA_TROT);
	}

	@Test
	void 인정분과_baseline과_hold_해제_preview는_현재_Domain_Command를_그대로_반영한다() {
		final Member member = managedMember("preview-removal", 20);
		member.changeProgressionBaseline(GeneralRidingGrade.LARGE_ARENA_TROT, STARTED_AT);
		member.changeSpecialApprovals(true, false);
		member.changeSpecialApprovals(false, false);

		final MemberClassProgressionProjection creditPreview = member.previewSpecialApprovalProgressionCredit(0);
		member.correctSpecialApprovalProgressionCredit(0);
		assertThat(member.currentClassProgressionProjection()).isEqualTo(creditPreview);

		member.changePromotionHold(GeneralRidingGrade.ROUND_TROT);
		final MemberClassProgressionProjection holdRemovalPreview = member.previewWithoutPromotionHold();
		member.removePromotionHold();
		assertThat(member.currentClassProgressionProjection()).isEqualTo(holdRemovalPreview);

		final MemberClassProgressionProjection baselineRemovalPreview = member.previewWithoutProgressionBaseline();
		member.removeProgressionBaseline();
		assertThat(member.currentClassProgressionProjection()).isEqualTo(baselineRemovalPreview);
	}

	private Member managedMember(String authSubject, int rideCount) {
		final Member member = Member.createManaged(authSubject, "미리보기 회원", "010-0000-0000", STARTED_AT);
		if (rideCount > 0) {
			member.adjustGeneralRideCount(rideCount);
		}
		return member;
	}
}
