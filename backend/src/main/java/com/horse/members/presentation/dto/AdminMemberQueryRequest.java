package com.horse.members.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminMemberQueryRequest(
	@Min(0)
	Integer page,
	@Min(1)
	@Max(100)
	Integer size,
	@Size(max = 100)
	@Schema(description = "회원 이름 또는 전화번호 부분 검색. 생략하거나 공백이면 전체 회원 조회")
	String query
) {
}
