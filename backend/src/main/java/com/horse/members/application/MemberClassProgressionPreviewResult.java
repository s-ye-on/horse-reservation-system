package com.horse.members.application;

import com.horse.members.domain.MemberClassProgressionProjection;

public record MemberClassProgressionPreviewResult(
	String stateToken,
	MemberClassProgressionProjection current,
	MemberClassProgressionProjection expected
) {
}
