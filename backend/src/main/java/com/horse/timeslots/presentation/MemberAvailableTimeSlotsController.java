package com.horse.timeslots.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.horse.timeslots.application.MemberAvailableTimeSlotsResult;
import com.horse.timeslots.application.MemberAvailableTimeSlotsService;
import com.horse.timeslots.presentation.dto.MemberAvailableTimeSlotsResponse;

@RestController
@RequestMapping("/api/timeslots")
public class MemberAvailableTimeSlotsController {

	private final MemberAvailableTimeSlotsService service;

	public MemberAvailableTimeSlotsController(MemberAvailableTimeSlotsService service) {
		this.service = service;
	}

	@GetMapping
	public MemberAvailableTimeSlotsResponse getAvailableTimeSlots(
		@AuthenticationPrincipal(expression = "subject") String authSubject,
		@RequestParam String date,
		@RequestParam String classType
	) {
		final MemberAvailableTimeSlotsResult result = service.getAvailableTimeSlots(
			authSubject,
			date,
			classType);
		return MemberAvailableTimeSlotsResponse.from(result);
	}
}
