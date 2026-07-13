package com.horse.members.presentation;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.members.application.AdminMemberQueryService;
import com.horse.members.presentation.dto.AdminMemberResponse;

@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberQueryController {

	private final AdminMemberQueryService service;

	public AdminMemberQueryController(AdminMemberQueryService service) {
		this.service = service;
	}

	@GetMapping
	public List<AdminMemberResponse> getMembers() {
		return service.getMembers().stream()
			.map(AdminMemberResponse::from)
			.toList();
	}

	@GetMapping("/{memberId}")
	public AdminMemberResponse getMember(@PathVariable Long memberId) {
		return AdminMemberResponse.from(service.getMember(memberId));
	}

}
