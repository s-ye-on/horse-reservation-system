package com.horse.members.presentation.dto;

import com.horse.members.application.MemberClassProgressionPreviewResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record MemberClassProgressionPreviewResponse(
	@Schema(
		description = "현재 progression 상태의 strong entity-tag. 변경 Command의 If-Match Header에 그대로 전달한다.",
		example = "\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"",
		pattern = "^\\\"[0-9a-f]{64}\\\"$",
		requiredMode = Schema.RequiredMode.REQUIRED)
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
