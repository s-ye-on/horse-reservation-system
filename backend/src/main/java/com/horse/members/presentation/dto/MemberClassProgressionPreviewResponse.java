package com.horse.members.presentation.dto;

import com.horse.members.application.MemberClassProgressionPreviewResult;

public record MemberClassProgressionPreviewResponse(
	String stateToken,
	MemberClassProgressionProjectionResponse current,
	MemberClassProgressionProjectionResponse expected
) {

	public static MemberClassProgressionPreviewResponse from(MemberClassProgressionPreviewResult result) {
		return new MemberClassProgressionPreviewResponse(
			result.stateToken(),
			MemberClassProgressionProjectionResponse.from(result.current()),
			MemberClassProgressionProjectionResponse.from(result.expected()));
	}
}
