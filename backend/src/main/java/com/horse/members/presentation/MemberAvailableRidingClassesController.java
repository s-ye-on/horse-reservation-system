package com.horse.members.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.members.application.MemberAvailableRidingClassesResult;
import com.horse.members.application.MemberAvailableRidingClassesService;
import com.horse.members.presentation.dto.MemberAvailableRidingClassesResponse;

@RestController
@RequestMapping("/api/me/eligible-classes")
public class MemberAvailableRidingClassesController {

	private final MemberAvailableRidingClassesService service;

	public MemberAvailableRidingClassesController(MemberAvailableRidingClassesService service) {
		this.service = service;
	}

	@GetMapping
	public MemberAvailableRidingClassesResponse getAvailableRidingClasses(
		@AuthenticationPrincipal(expression = "subject") String authSubject
	) {
		final MemberAvailableRidingClassesResult result = service.getAvailableRidingClasses(authSubject);
		return MemberAvailableRidingClassesResponse.from(result);
	}

}
