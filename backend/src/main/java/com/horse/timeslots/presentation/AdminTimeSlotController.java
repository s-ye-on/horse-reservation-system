package com.horse.timeslots.presentation;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.horse.timeslots.application.AdminTimeSlotService;
import com.horse.timeslots.presentation.dto.TimeSlotCapacityUpdateRequest;
import com.horse.timeslots.presentation.dto.TimeSlotCreateRequest;
import com.horse.timeslots.presentation.dto.TimeSlotResponse;
import com.horse.timeslots.presentation.dto.TimeSlotStatusUpdateRequest;

@RestController
@RequestMapping("/api/admin/timeslots")
public class AdminTimeSlotController {

	private final AdminTimeSlotService service;

	public AdminTimeSlotController(AdminTimeSlotService service) {
		this.service = service;
	}

	@GetMapping
	public List<TimeSlotResponse> getTimeSlots() {
		return service.getTimeSlots().stream()
			.map(TimeSlotResponse::from)
			.toList();
	}

	@GetMapping("/history")
	public List<TimeSlotResponse> getTimeSlotHistory() {
		return service.getTimeSlotHistory().stream()
			.map(TimeSlotResponse::from)
			.toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TimeSlotResponse createTimeSlot(@RequestBody TimeSlotCreateRequest request) {
		return TimeSlotResponse.from(service.createTimeSlot(
			request.lessonDate(),
			request.startTime(),
			request.totalCapacity(),
			request.roundArenaCapacity(),
			request.classCapacities()));
	}

	@PatchMapping("/{timeSlotId}")
	public TimeSlotResponse changeClosedStatus(
		@PathVariable Long timeSlotId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@RequestBody TimeSlotStatusUpdateRequest request
	) {
		return TimeSlotResponse.from(service.changeClosedStatus(
			timeSlotId,
			request.closed(),
			adminSubject));
	}

	@PutMapping("/{timeSlotId}/capacity")
	public TimeSlotResponse changeCapacity(
		@PathVariable Long timeSlotId,
		@RequestBody TimeSlotCapacityUpdateRequest request
	) {
		return TimeSlotResponse.from(service.changeCapacity(
			timeSlotId,
			request.totalCapacity(),
			request.roundArenaCapacity(),
			request.classCapacities()));
	}

	@DeleteMapping("/{timeSlotId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteTimeSlot(@PathVariable Long timeSlotId) {
		service.deleteTimeSlot(timeSlotId);
	}

}
